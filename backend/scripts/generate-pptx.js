/**
 * PPT 方案演示生成脚本（深蓝科技风）
 * 用法: node generate-pptx.js <input.json> <output.pptx> [template.pptx]
 *   input.json: ToolContext.toMap() 的 JSON 序列化
 *   output.pptx: 生成的 PPT 路径
 *   template.pptx: 可选品牌模板
 */
const fs = require('fs');
const pptxgen = require('pptxgenjs');

const inputPath = process.argv[2];
const outputPath = process.argv[3];
const templatePath = process.argv[4];
if (!inputPath || !outputPath) {
  console.error('用法: node generate-pptx.js <input.json> <output.pptx> [template.pptx]');
  process.exit(1);
}

const ctx = JSON.parse(fs.readFileSync(inputPath, 'utf8'));
const projectName = ctx.projectName || 'GIS 解决方案';
const now = new Date().toLocaleString('zh-CN');

// 深蓝科技风配色
const COLOR_BG = '0A1A2F';
const COLOR_ACCENT = '00E5D4';
const COLOR_WHITE = 'FFFFFF';
const COLOR_GRAY = 'AAB4C0';
const COLOR_DARK = '0D2436';

const pptx = new pptxgen();
pptx.defineLayout({ name: 'WIDE', width: 13.333, height: 7.5 });
pptx.layout = 'WIDE';
if (templatePath && fs.existsSync(templatePath)) {
  try { pptx.load(templatePath); } catch (e) { /* 模板加载失败则用默认 */ }
}

function addBg(slide) {
  slide.background = { color: COLOR_BG };
}

function titleSlide() {
  const s = pptx.addSlide();
  addBg(s);
  s.addText(projectName, { x: 0.8, y: 2.6, w: 11.7, h: 1.4, fontSize: 40, bold: true, color: COLOR_WHITE, align: 'left', fontFace: '微软雅黑' });
  s.addText('解决方案概览', { x: 0.8, y: 4.0, w: 11.7, h: 0.6, fontSize: 20, color: COLOR_ACCENT, align: 'left', fontFace: '微软雅黑' });
  s.addText('生成时间：' + now, { x: 0.8, y: 6.4, w: 11.7, h: 0.4, fontSize: 13, color: COLOR_GRAY, align: 'left' });
  // 装饰线条
  s.addShape(pptx.ShapeType.rect, { x: 0.8, y: 4.7, w: 3.2, h: 0.06, fill: { color: COLOR_ACCENT } });
}

function contentSlide(title, bulletsText) {
  const s = pptx.addSlide();
  addBg(s);
  // 标题栏
  s.addShape(pptx.ShapeType.rect, { x: 0, y: 0, w: 13.333, h: 1.1, fill: { color: COLOR_DARK } });
  s.addShape(pptx.ShapeType.rect, { x: 0, y: 1.1, w: 13.333, h: 0.06, fill: { color: COLOR_ACCENT } });
  s.addText(title, { x: 0.8, y: 0.2, w: 11.7, h: 0.7, fontSize: 26, bold: true, color: COLOR_WHITE, fontFace: '微软雅黑' });
  // 要点
  const items = String(bulletsText || '').split('\n').map((l) => l.trim()).filter(Boolean);
  if (items.length) {
    s.addText(
      items.map((t, i) => ({ text: t, options: { bullet: { code: '2022' }, color: COLOR_WHITE, fontSize: 16, fontFace: '微软雅黑', paraSpaceAfter: 10 } })),
      { x: 0.9, y: 1.5, w: 11.5, h: 5.5, valign: 'top' }
    );
  }
  return s;
}

function tableSlide(title, headers, rows) {
  const s = pptx.addSlide();
  addBg(s);
  s.addShape(pptx.ShapeType.rect, { x: 0, y: 0, w: 13.333, h: 1.1, fill: { color: COLOR_DARK } });
  s.addShape(pptx.ShapeType.rect, { x: 0, y: 1.1, w: 13.333, h: 0.06, fill: { color: COLOR_ACCENT } });
  s.addText(title, { x: 0.8, y: 0.2, w: 11.7, h: 0.7, fontSize: 26, bold: true, color: COLOR_WHITE, fontFace: '微软雅黑' });
  const body = [headers.map((h) => ({ text: h, options: { bold: true, color: COLOR_BG, fill: { color: COLOR_ACCENT }, fontFace: '微软雅黑', fontSize: 14 } })),
    ...rows.map((r) => r.map((c) => ({ text: String(c ?? ''), options: { color: COLOR_WHITE, fontFace: '微软雅黑', fontSize: 13 } })))];
  s.addTable(body, { x: 0.8, y: 1.5, w: 11.7, colW: headers.map(() => 11.7 / headers.length), border: { type: 'solid', color: '1E3A52', pt: 1 }, fill: { color: COLOR_DARK }, align: 'left', valign: 'middle' });
  return s;
}

// 封面
titleSlide();

// 一、需求分析
if (ctx.requirements) {
  const r = ctx.requirements;
  const lines = [];
  if (r.functional) lines.push(...r.functional.map((f) => '功能需求：' + f));
  if (r.nonFunctional) lines.push(...r.nonFunctional.map((f) => '非功能需求：' + f));
  if (r.constraints) lines.push(...r.constraints.map((f) => '约束条件：' + f));
  if (r.industry) lines.push('行业场景：' + r.industry);
  contentSlide('需求分析', lines.join('\n'));
}

// 二、产品选型
if (ctx.productSelection && ctx.productSelection.length) {
  const rows = ctx.productSelection.map((p) => [p.productName || '', p.version || '', p.reason || '', p.coverage != null ? p.coverage + '%' : '']);
  tableSlide('产品选型清单', ['产品名称', '版本', '推荐理由', '需求覆盖率'], rows);
}

// 三、参考案例
if (ctx.caseRecommendations && ctx.caseRecommendations.length) {
  const lines = ctx.caseRecommendations.map((c) =>
    (c.caseName || '案例') + '：' + [c.scenario ? '场景-' + c.scenario : '', c.keyEffect ? '成效-' + c.keyEffect : ''].filter(Boolean).join('，')
  );
  contentSlide('参考案例', lines.join('\n'));
}

// 四、竞品对比
if (ctx.competitorAnalysis && ctx.competitorAnalysis.length) {
  const lines = ctx.competitorAnalysis.map((c) =>
    (c.competitorName || '竞品') + '：\n  ' + [c.ourAdvantage ? '优势-' + c.ourAdvantage : '', c.ourDisadvantage ? '劣势-' + c.ourDisadvantage : '', c.recommendation ? '建议-' + c.recommendation : ''].filter(Boolean).join('\n  ')
  );
  contentSlide('竞品对比分析', lines.join('\n'));
}

// 五、技术架构
if (ctx.architectureDiagram) {
  const lines = [];
  if (ctx.architectureDiagram.description) lines.push(ctx.architectureDiagram.description);
  if (ctx.architectureDiagram.mermaid) lines.push('（架构图 Mermaid 源码详见 Word / Markdown 导出）');
  contentSlide('技术架构', lines.join('\n'));
}

// 六、方案大纲
if (ctx.solutionOutline) {
  const lines = [];
  if (ctx.solutionOutline.overview) lines.push(ctx.solutionOutline.overview);
  if (ctx.solutionOutline.sections) lines.push(...ctx.solutionOutline.sections.map((s) => (s.title || '') + '：' + (s.keyPoints || '')));
  contentSlide('方案大纲', lines.join('\n'));
}

// 七、方案质检
if (ctx.qualityCheck) {
  const q = ctx.qualityCheck;
  const lines = [];
  if (q.overallScore != null) lines.push('整体评分：' + q.overallScore + (q.level ? '（' + q.level + '）' : ''));
  if (q.dimensions) q.dimensions.forEach((d) => lines.push((d.dimension || '') + '：' + (d.score != null ? d.score : '') + (d.comment ? ' - ' + d.comment : '')));
  contentSlide('方案质检', lines.join('\n'));
}

// 八、解决方案正文（摘要）
if (ctx.solutionText) {
  const summary = String(ctx.solutionText).replace(/[#*\|`>]/g, '').slice(0, 1200);
  contentSlide('解决方案正文（摘要）', summary);
}

pptx.writeFile({ fileName: outputPath }).then(() => {
  console.log('PPTX generated: ' + outputPath);
}).catch((e) => {
  console.error('PPTX generation failed:', e);
  process.exit(1);
});
