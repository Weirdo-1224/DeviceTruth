# 第4批浏览器核验：4.1 状态同步（流收尾回读）+ 4.2 多轮历史（轮次选择/刷新不覆盖/执行中刷新恢复）。
# 注意：目标会话会被真实提交新 Run（真模 deepseek-flash），脚本不假设固定轮次数。
import sys
import time
import json
import urllib.request

from playwright.sync_api import sync_playwright, expect as expect_page

BASE = "http://127.0.0.1:8081"
API = "http://127.0.0.1:8080"
CONV_TWO_RUNS = "9cc4c503-8345-47c9-a82f-3f3c545fe9de"  # 历史两轮会话（轮次会继续增长）

errors = []


def check(condition, label):
    print(("PASS " if condition else "FAIL ") + label)
    if not condition:
        errors.append(label)


def latest_run_category():
    with urllib.request.urlopen(f"{API}/api/conversations/{CONV_TWO_RUNS}", timeout=15) as r:
        d = json.load(r)
    runs = d.get("runs") or []
    if not runs:
        return None, 0
    last = runs[-1]
    return (last.get("result") or {}).get("category"), len(runs)


with sync_playwright() as p:
    browser = p.chromium.launch(channel="chrome", headless=True)
    page = browser.new_page(viewport={"width": 1400, "height": 950})
    page.goto(BASE, wait_until="networkidle")
    page.evaluate(f'window.localStorage.setItem("devicetruth.conversation-id", "{CONV_TWO_RUNS}")')
    page.reload(wait_until="networkidle")
    page.wait_for_timeout(1800)

    # ---- 4.2 轮次选择 ----
    chips = page.locator(".run-chip")
    initial_count = chips.count()
    check(initial_count >= 2, f"4.2 轮次选择器显示多轮（实际 {initial_count}）")
    active_index = page.evaluate(
        "([...document.querySelectorAll('.run-chip')].findIndex(b => b.classList.contains('active')))")
    check(active_index == initial_count - 1, "4.2 默认查看最新轮（active 为最后一个 chip）")
    category, _ = latest_run_category()
    body = page.inner_text("body")
    expected_title = "候选原因" if category == "POSSIBLE_CAUSES" else ("资料断言" if category == "KNOWLEDGE" else None)
    check(expected_title is None or f"{expected_title}（" in body,
          f"4.2 最新轮内容渲染（category={category}）")

    chips.nth(0).click()
    page.wait_for_timeout(500)
    body = page.inner_text("body")
    check("资料断言（2）" in body, "4.2 切到第1轮显示 KNOWLEDGE 结果")
    check("第 1 轮" in page.locator(".run-chip.active").inner_text(), "4.2 选中态跟随第1轮")

    page.get_by_role("button", name="刷新", exact=True).click()
    page.wait_for_timeout(1200)
    check("资料断言（2）" in page.inner_text("body"), "4.2 刷新不强制覆盖主动选择（仍停留在第1轮）")

    # ---- 清理：若最新轮仍等待补答，先用既有补答路径恢复，保证后续提交测试可控 ----
    def answer_pending_if_any(reply_text, timeout_s=15):
        try:
            expect_page(page.get_by_role("heading", name="需要补充信息")).to_have_count(1, timeout=timeout_s * 1000)
        except AssertionError:
            return False
        page.locator(".question textarea").fill(reply_text)
        page.get_by_role("button", name="提交补充信息").click()
        return True

    for _ in range(4):
        page.wait_for_timeout(1500)
        if not answer_pending_if_any("EV-DEMO-004；交流慢充刚插枪就中断，无故障码。"):
            break
        try:
            expect_page(page.locator(".pro-page .summary")).to_be_visible(timeout=180_000)
        except AssertionError:
            continue

    chips_before = page.locator(".run-chip").count()

    # ---- 4.1 状态同步：提交新一轮 ----
    page.locator(".composer textarea").fill("车辆充电时偶尔中断，请再核查可能原因。")
    page.get_by_role("button", name="发送", exact=True).click()
    expect_page(page.locator(".run-chip")).to_have_count(chips_before + 1, timeout=15_000)
    check(page.locator(".pro-page", has_text="调查进行中").count() >= 1,
          "4.1 执行中渲染‘调查进行中’状态卡（区别于终态报告）")

    # 执行中刷新：默认回到最新轮并恢复跟踪；真模可能进入 WAITING 追问
    page.reload(wait_until="networkidle")
    page.wait_for_timeout(1500)
    check(page.locator(".pro-page", has_text="调查进行中").count() >= 1
          or "本轮摘要" in page.inner_text("body"), "4.1/4.2 执行中刷新后恢复跟踪（状态卡或结果）")

    answered = False
    for _ in range(4):
        if answer_pending_if_any("EV-DEMO-004；交流慢充，刚插枪就中断，无故障码。"):
            answered = True
            try:
                expect_page(page.locator(".pro-page .summary")).to_be_visible(timeout=180_000)
                break
            except AssertionError:
                continue
        else:
            break
    if answered:
        check(True, "4.1 WAITING 状态渲染问题区，补答沿同一 Run 恢复（未新增轮次）")
    expect_page(page.locator(".pro-page .summary")).to_be_visible(timeout=180_000)
    check("本轮摘要" in page.inner_text("body"), "4.1 流收尾后经服务器回读显示本轮摘要")
    check(page.locator(".run-chip").count() == chips_before + 1, "4.2 轮次数保持提交后的值（补答不新增轮次）")
    check("资料断言（2）" not in page.inner_text("body"), "4.2 新结果展示不残留旧轮内容")

    page.screenshot(path="outputs/validation/pro-page-4.1-4.2.png", full_page=True)
    browser.close()

sys.exit(1 if errors else 0)
