const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

// 解析命令行参数
const args = process.argv.slice(2);
const url = args[0];
const outputPath = args[1];

if (!url || !outputPath) {
    console.error('用法: node web-screenshot.js <url> <outputPath> [options]');
    process.exit(1);
}

// 解析选项
const options = {
    fullPage: true,
    waitUntil: 'networkidle',
    timeout: 30000,
    viewportWidth: null,
    viewportHeight: null,
    deviceScaleFactor: 2,
    cookiesFile: null
};

args.slice(2).forEach(arg => {
    if (arg.startsWith('--fullPage=')) {
        options.fullPage = arg.split('=')[1] === 'true';
    } else if (arg.startsWith('--waitUntil=')) {
        options.waitUntil = arg.split('=')[1];
    } else if (arg.startsWith('--timeout=')) {
        options.timeout = parseInt(arg.split('=')[1]);
    } else if (arg.startsWith('--viewportWidth=')) {
        options.viewportWidth = parseInt(arg.split('=')[1]);
    } else if (arg.startsWith('--viewportHeight=')) {
        options.viewportHeight = parseInt(arg.split('=')[1]);
    } else if (arg.startsWith('--deviceScaleFactor=')) {
        options.deviceScaleFactor = parseFloat(arg.split('=')[1]);
    } else if (arg.startsWith('--cookiesFile=')) {
        options.cookiesFile = arg.split('=')[1];
    }
});

async function autoScrollPage(page, timeout) {
    await page.evaluate(async maxDuration => {
        const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
        const startTime = Date.now();
        let lastHeight = -1;
        let stableRounds = 0;

        while (Date.now() - startTime < maxDuration) {
            const viewportHeight = window.innerHeight || 900;
            const currentHeight = Math.max(
                document.body.scrollHeight,
                document.documentElement.scrollHeight
            );

            window.scrollBy(0, Math.max(400, Math.floor(viewportHeight * 0.85)));
            await delay(250);

            const nextHeight = Math.max(
                document.body.scrollHeight,
                document.documentElement.scrollHeight
            );

            if (nextHeight === lastHeight && window.innerHeight + window.scrollY >= nextHeight - 4) {
                stableRounds += 1;
            } else {
                stableRounds = 0;
            }

            lastHeight = nextHeight;

            if (stableRounds >= 3) {
                break;
            }
        }

        window.scrollTo(0, 0);
        await delay(200);
    }, Math.min(timeout, 15000));
}

async function forceLazyImages(page) {
    await page.evaluate(() => {
        const selectors = ['img', 'source', '[data-src]', '[data-original]', '[data-lazy-src]'];

        document.querySelectorAll(selectors.join(',')).forEach(node => {
            if (node.tagName === 'IMG') {
                node.loading = 'eager';
                node.decoding = 'sync';
            }

            const dataSrc = node.getAttribute('data-src');
            const dataOriginal = node.getAttribute('data-original');
            const dataLazySrc = node.getAttribute('data-lazy-src');

            if (node.tagName === 'IMG' && !node.getAttribute('src')) {
                const fallbackSrc = dataSrc || dataOriginal || dataLazySrc;
                if (fallbackSrc) {
                    node.setAttribute('src', fallbackSrc);
                }
            }

            if (node.tagName === 'SOURCE' && !node.getAttribute('srcset')) {
                const fallbackSrcSet = dataSrc || dataOriginal || dataLazySrc;
                if (fallbackSrcSet) {
                    node.setAttribute('srcset', fallbackSrcSet);
                }
            }
        });
    });
}

async function waitForImages(page, timeout) {
    await page.evaluate(async maxDuration => {
        const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
        const startTime = Date.now();

        while (Date.now() - startTime < maxDuration) {
            const pendingImages = Array.from(document.images).filter(img => {
                const style = window.getComputedStyle(img);
                const visible = style.display !== 'none' && style.visibility !== 'hidden';
                const hasSource = Boolean(img.currentSrc || img.src);
                return visible && hasSource && !img.complete;
            });

            if (pendingImages.length === 0) {
                await delay(300);

                const recheckPending = Array.from(document.images).filter(img => {
                    const style = window.getComputedStyle(img);
                    const visible = style.display !== 'none' && style.visibility !== 'hidden';
                    const hasSource = Boolean(img.currentSrc || img.src);
                    return visible && hasSource && !img.complete;
                });

                if (recheckPending.length === 0) {
                    break;
                }
            }

            await delay(250);
        }
    }, Math.min(timeout, 10000));
}

async function settleLongPage(page, timeout) {
    await forceLazyImages(page);
    await autoScrollPage(page, timeout);
    await forceLazyImages(page);
    await waitForImages(page, timeout);
}

async function captureScreenshot() {
    let browser = null;
    try {
        console.log(`开始截图: ${url}`);
        
        browser = await chromium.launch({
            headless: true
        });
        
        const page = await browser.newPage({
            deviceScaleFactor: Number.isFinite(options.deviceScaleFactor) && options.deviceScaleFactor > 0
                ? options.deviceScaleFactor
                : 2
        });
        
        // 设置视口
        if (options.viewportWidth && options.viewportHeight) {
            await page.setViewportSize({
                width: options.viewportWidth,
                height: options.viewportHeight
            });
        }
        
        // 加载 Cookie（如果提供）
        if (options.cookiesFile && fs.existsSync(options.cookiesFile)) {
            try {
                const cookiesJson = fs.readFileSync(options.cookiesFile, 'utf8');
                const cookies = JSON.parse(cookiesJson);
                if (Array.isArray(cookies) && cookies.length > 0) {
                    // 提取域名（从 URL）
                    const urlObj = new URL(url);
                    const domain = urlObj.hostname;
                    
                    // 设置 Cookie
                    await page.context().addCookies(cookies.map(cookie => ({
                        name: cookie.name,
                        value: cookie.value,
                        domain: cookie.domain || domain,
                        path: cookie.path || '/',
                        expires: cookie.expires || Math.floor(Date.now() / 1000) + 86400, // 默认1天后过期
                        httpOnly: cookie.httpOnly || false,
                        secure: cookie.secure || false,
                        sameSite: cookie.sameSite || 'Lax'
                    })));
                    console.log(`已加载 ${cookies.length} 个 Cookie`);
                }
            } catch (error) {
                throw new Error(`加载 Cookie 失败: ${error.message}`);
            }
        }
        
        // 导航到页面
        await page.goto(url, {
            waitUntil: options.waitUntil,
            timeout: options.timeout
        });
        
        // 等待页面完全加载（额外等待动态内容）
        await page.waitForTimeout(1000);

        if (options.fullPage) {
            console.log('开始预加载长页内容');
            await settleLongPage(page, options.timeout);
        } else {
            await forceLazyImages(page);
            await waitForImages(page, options.timeout);
        }
        
        // 截图
        console.log(`截图选项: fullPage=${options.fullPage}, waitUntil=${options.waitUntil}`);
        await page.screenshot({
            path: outputPath,
            fullPage: options.fullPage,
            type: 'png'
        });
        
        console.log(`截图成功保存到: ${outputPath}`);
        return 0;
        
    } catch (error) {
        console.error(`截图失败: ${error.message}`);
        console.error(error.stack);
        return 1;
    } finally {
        if (browser) {
            await browser.close();
        }
    }
}

captureScreenshot()
    .then(code => {
        process.exitCode = code;
    })
    .catch(error => {
        console.error(`截图异常: ${error.message}`);
        console.error(error.stack);
        process.exitCode = 1;
    });
