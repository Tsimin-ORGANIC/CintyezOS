/*
 * 把 KaTeX 重新打包进 kemon-os-v2.html（字体转 base64，离线可用）。
 * 用法：npm pack katex@0.16.47 && tar -xzf katex-0.16.47.tgz
 *       node tools/bundle-katex.js ./package
 * 脚本只替换 <style id="katex-css"> / <script id="katex-js"> 两段，其余内容不动。
 */
const fs = require('fs');
const path = require('path');

const pkg = path.resolve(process.argv[2] || '');
const htmlPath = path.resolve(__dirname, '..', 'kemon-os-v2.html');
if (!pkg || !fs.existsSync(path.join(pkg, 'dist/katex.min.js'))){
  console.error('请传入解包后的 katex package 目录');
  process.exit(1);
}

let css = fs.readFileSync(path.join(pkg, 'dist/katex.min.css'), 'utf8');
css = css.replace(/@font-face\{([^}]*)\}/g, (block, body) => {
  const m = body.match(/url\(fonts\/([^)]+\.woff2)\)/);
  if (!m) return block;
  const b64 = fs.readFileSync(path.join(pkg, 'dist/fonts', m[1])).toString('base64');
  body = body.replace(/src:[^;}]+;?/, `src:url(data:font/woff2;base64,${b64}) format("woff2")`);
  return '@font-face{' + body + '}';
});

const js = fs.readFileSync(path.join(pkg, 'dist/katex.min.js'), 'utf8');
let html = fs.readFileSync(htmlPath, 'utf8');
const styleRe = /<style id="katex-css">[\s\S]*?<\/style>/;
const scriptRe = /<script id="katex-js">[\s\S]*?<\/script>/;
if (!styleRe.test(html) || !scriptRe.test(html)){
  console.error('HTML 里找不到 katex-css / katex-js 槽位');
  process.exit(1);
}
const style = '<style id="katex-css">\n/* KaTeX · 字体已内嵌为 woff2 data URI */\n' + css + '\n</style>';
const script = '<script id="katex-js">\n' + js + '\n</script>';
html = html.replace(styleRe, () => style).replace(scriptRe, () => script);
fs.writeFileSync(htmlPath, html);
console.log('katex bundled · html', html.length, 'bytes');
