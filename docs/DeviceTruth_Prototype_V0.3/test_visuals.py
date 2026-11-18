from playwright.sync_api import sync_playwright
from pathlib import Path
base=Path('/mnt/data/DeviceTruth_Prototype_V0.3')
with sync_playwright() as p:
    b=p.chromium.launch(headless=True,executable_path='/usr/bin/chromium', args=['--no-sandbox'])
    page=b.new_page(viewport={'width':1600,'height':1000},device_scale_factor=1)
    errors=[]
    page.on('pageerror',lambda e:errors.append(str(e)))
    html=(base/'index.html').read_text()
    html=html.replace('<link rel="stylesheet" href="assets/styles.css" />','<style>'+ (base/'assets/styles.css').read_text() +'</style>')
    html=html.replace('<script src="assets/app.js"></script>','<script>'+ (base/'assets/app.js').read_text() +'</script>')
    page.set_content(html,wait_until='domcontentloaded')
    print('title:',page.title())
    for mode in ['pro','consumer','admin']:
        page.locator(f'.previewtab[data-view="{mode}"]').click()
        page.wait_for_timeout(250)
        page.screenshot(path=str(base/f'screenshots/{mode}-desktop.png'),full_page=True)
        print(mode,'visible:',page.locator(f'#{mode}').is_visible(),'screenshot px','scroll height',page.evaluate('document.documentElement.scrollHeight'))
    page.locator('.previewtab[data-view="pro"]').click()
    page.locator('[data-focus="1"]').click()
    assert page.locator('#focus-title').inner_text()=='诊断缓冲区与报警'
    assert page.locator('#facts-grid').inner_text().find('未取得')>=0
    page.locator('[data-compose-mode="observe"]').click()
    page.locator('#pro-input').fill('模拟观察：铭牌尚未拍摄')
    page.locator('#pro-send').click()
    assert '未核实' in page.locator('#pro-submitted').inner_text()
    page.locator('[data-evidence="configuration"]').click()
    assert page.locator('#overlay').is_visible()
    page.keyboard.press('Escape')
    assert not page.locator('#overlay').is_visible()
    page.locator('.previewtab[data-view="consumer"]').click()
    page.locator('[data-answer="暂时无法确认"]').click()
    assert page.locator('#consumer-input').input_value()=='暂时无法确认'
    page.locator('#consumer-form button[type="submit"]').click()
    assert '已更新提问' in page.locator('#consumer-submitted').inner_text()
    page.locator('.previewtab[data-view="admin"]').click()
    page.locator('[data-admin-nav="users"]').first.click()
    assert page.locator('#admin-module').is_visible()
    assert page.locator('#admin-title').inner_text()=='用户与权限'
    print('click tests passed; errors:',errors)
    assert not errors
    page.set_content(html, wait_until='domcontentloaded')
    page.set_viewport_size({'width':1024,'height':900})
    page.locator('.previewtab[data-view="pro"]').click()
    page.wait_for_timeout(250)
    page.screenshot(path=str(base/'screenshots/pro-tablet.png'),full_page=True)
    page.set_viewport_size({'width':390,'height':844})
    page.screenshot(path=str(base/'screenshots/pro-mobile.png'),full_page=True)
    print('mobile horizontal overflow:',page.evaluate('document.documentElement.scrollWidth > window.innerWidth'))
    b.close()
