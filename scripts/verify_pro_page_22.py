# 2.2 一次性浏览器核验：打开 8081 专业页，用真实 COMPLETED Run 渲染并检查无假入口。
import sys
import time
import urllib.request

from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:8081"
CONV_ID = "ca1ae174-37ff-4dff-be31-38bdbc6b33b0"  # 1.1 核验用的真实会话（COMPLETED / POSSIBLE_CAUSES / 5 claims）

errors = []


def check(condition, label):
    print(("PASS " if condition else "FAIL ") + label)
    if not condition:
        errors.append(label)


with sync_playwright() as p:
    browser = p.chromium.launch(channel="chrome", headless=True)
    page = browser.new_page()
    page.goto(BASE, wait_until="networkidle")
    page.wait_for_timeout(1500)

    # 通过 localStorage 直接定位到目标会话再刷新，等价于用户从历史列表点开
    page.evaluate(f'window.localStorage.setItem("devicetruth.conversation-id", "{CONV_ID}")')
    page.reload(wait_until="networkidle")
    page.wait_for_timeout(2000)

    body = page.inner_text("body")

    check("当前任务" in body, "顶部任务区渲染（当前任务）")
    check("候选原因（5）" in body, "判断区按 category 渲染候选原因（5 条）")
    check("本轮摘要" in body, "中央详情区渲染本轮摘要")
    check("本轮建议（本轮）" in body, "本轮建议分组标注")
    check("不确定点（本轮）" in body, "不确定点分组标注")
    check("限制（本轮）" in body, "限制分组标注")
    check("未关联设备" in body or "CAR" in body or "EV02" in body, "设备 chip 渲染（关联或未关联）")
    check("已完成" in body, "状态渲染")
    check("本轮已取回资料" in body, "已取回资料清单渲染")
    check("个人模式" not in body, "无个人模式假入口")
    check("关系图" not in body, "无关系图假入口")
    check("深入调查" not in body, "无未实现的深入调查按钮（属 5.1）")

    page.screenshot(path="outputs/validation/pro-page-2.2.png", full_page=True)
    browser.close()

sys.exit(1 if errors else 0)
