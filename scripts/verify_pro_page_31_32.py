# 第3批浏览器核验：3.1 判断选中（零提交）+ 3.2 证据抽屉（字段/关闭/失败轮证据）。
import sys
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:8081"
CONV_OK = "ca1ae174-37ff-4dff-be31-38bdbc6b33b0"   # COMPLETED / 5 claims / 9 refs
CONV_FAILED = "9641d9ab-2b97-4e76-88f6-c037dfc508b2"  # FAILED / 4 refs，无结果

errors = []


def check(condition, label):
    print(("PASS " if condition else "FAIL ") + label)
    if not condition:
        errors.append(label)


with sync_playwright() as p:
    browser = p.chromium.launch(channel="chrome", headless=True)
    page = browser.new_page(viewport={"width": 1400, "height": 950})

    # ---- 3.1 判断选中：只切 UI，零提交 ----
    api_requests = []
    page.on("request", lambda r: api_requests.append(r.method + " " + r.url) if "/api/" in r.url else None)
    page.goto(BASE, wait_until="networkidle")
    page.evaluate(f'window.localStorage.setItem("devicetruth.conversation-id", "{CONV_OK}")')
    page.reload(wait_until="networkidle")
    page.wait_for_timeout(1800)
    api_requests.clear()  # 只统计交互期间的请求

    body = page.inner_text("body")
    check("候选原因（5）" in body, "3.1 类别标题正确（候选原因（5））")
    check("判断详情" in body, "3.1 默认选中第一条并显示判断详情")

    claim_buttons = page.locator("button.claim-select")
    check(claim_buttons.count() == 5, f"3.1 判断列表 5 条（实际 {claim_buttons.count()}）")

    first_text = page.locator(".claim-detail .detail-text").inner_text()
    claim_buttons.nth(1).click()
    page.wait_for_timeout(300)
    second_text = page.locator(".claim-detail .detail-text").inner_text()
    check(first_text != second_text, "3.1 点击第二条判断后详情切换")
    claim_buttons.nth(0).click()
    page.wait_for_timeout(300)
    check(page.locator(".claim-detail .detail-text").inner_text() == first_text, "3.1 切回第一条详情还原")
    posts = [r for r in api_requests if r.startswith("POST")]
    check(not posts, f"3.1 选择判断期间零 POST/模型请求（POST 数={len(posts)}）")
    check("当前无已关联引用" in page.inner_text("body") or "引用暂不可用" in page.inner_text("body"),
          "3.1 无引用判断有说明文字")

    # ---- 3.2 证据抽屉：字段真实、可关闭、缺页码不伪造 ----
    api_requests.clear()
    page.locator(".claim-detail button.citation-ref").first.click()
    page.wait_for_timeout(400)
    drawer_text = page.locator(".evidence-drawer").inner_text()
    check("证据详情" in drawer_text, "3.2 抽屉打开")
    for token in ["来源类型", "来源 ID", "来源版本", "切片", "数据性质", "摘录"]:
        check(token in drawer_text, f"3.2 抽屉字段存在：{token}")
    check("章节：" in drawer_text, "3.2 缺页码时展示实际章节，不伪造页码")
    check("第 页" not in drawer_text.replace(" ", ""), "3.2 无伪造页码")
    page.locator(".evidence-drawer button[aria-label='关闭证据抽屉']").click()
    page.wait_for_timeout(300)
    check(page.locator(".evidence-drawer").count() == 0, "3.2 抽屉可关闭")
    check(page.locator(".claim-detail .detail-text").inner_text() == first_text,
          "3.2 关闭抽屉后判断焦点保持（回到原焦点）")
    posts = [r for r in api_requests if r.startswith("POST")]
    check(not posts, f"3.2 打开/关闭抽屉期间零 POST（POST 数={len(posts)}）")
    page.screenshot(path="outputs/validation/pro-page-3.1-3.2.png", full_page=True)

    # ---- 3.2 失败轮：readReferences 加载已登记证据 ----
    api_requests.clear()
    page.evaluate(f'window.localStorage.setItem("devicetruth.conversation-id", "{CONV_FAILED}")')
    page.reload(wait_until="networkidle")
    page.wait_for_timeout(2000)
    body = page.inner_text("body")
    check("本次没有得到答案" in body, "3.2 失败轮显示安全失败卡片")
    check("本轮已取回证据" in body, "3.2 失败轮显示已取回证据区（readReferences 生效）")
    page.locator("section[role='alert'] button.citation-ref").first.click()
    page.wait_for_timeout(400)
    drawer_text = page.locator(".evidence-drawer").inner_text()
    check("证据详情" in drawer_text and "数据性质" in drawer_text, "3.2 失败轮证据抽屉可打开且字段完整")
    page.screenshot(path="outputs/validation/pro-page-3.2-failed.png", full_page=True)
    browser.close()

sys.exit(1 if errors else 0)
