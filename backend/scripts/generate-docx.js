/**
 * Word 方案文档生成脚本（标书正式风格）
 * 用法: node generate-docx.js <input.json> <output.docx>
 *   input.json: ToolContext.toMap() 的 JSON 序列化
 *   output.docx: 生成的 Word 文件路径
 *
 * 排版规范：A4、标准页边距、黑体标题 + 宋体正文、自动目录、页眉页脚
 */
const fs = require('fs');
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType,
  Table, TableRow, TableCell, WidthType, BorderStyle, ShadingType,
  Header, Footer, PageNumber, TableOfContents, LevelFormat, PageBreak,
} = require('docx');

const inputPath = process.argv[2];
const outputPath = process.argv[3];
if (!inputPath || !outputPath) {
  console.error('用法: node generate-docx.js <input.json> <output.docx>');
  process.exit(1);
}

const ctx = JSON.parse(fs.readFileSync(inputPath, 'utf8'));
const projectName = ctx.projectName || 'GIS 解决方案';
const now = new Date().toLocaleString('zh-CN');

// 中文默认字体
const FONT = '宋体';
const FONT_HEAD = '黑体';

function run(text, opts = {}) {
  return new TextRun({
    text: text || '',
    font: opts.bold ? FONT_HEAD : FONT,
    size: opts.size || 24, // 12pt = 24 half-points
    bold: opts.bold || false,
    color: opts.color || '000000',
    ...opts.extra,
  });
}

function heading(text, level = 1) {
  return new Paragraph({
    heading: level === 1 ? HeadingLevel.HEADING_1 : HeadingLevel.HEADING_2,
    spacing: { before: level === 1 ? 360 : 240, after: 160 },
    children: [run(text, { bold: true, size: level === 1 ? 32 : 28 })],
  });
}

function para(text, opts = {}) {
  return new Paragraph({
    spacing: { after: 120, line: 360 },
    alignment: opts.alignment || AlignmentType.LEFT,
    children: [run(text, opts)],
  });
}

function bullets(items) {
  if (!items || !items.length) return [];
  return items.filter(Boolean).map((it) =>
    new Paragraph({
      numbering: { reference: 'bullets', level: 0 },
      spacing: { after: 80, line: 320 },
      children: [run(typeof it === 'string' ? it : JSON.stringify(it))],
    })
  );
}

// 三线表
function threeLineTable(headers, rows) {
  const borders = {
    top: { style: BorderStyle.SINGLE, size: 8, color: '000000' },
    bottom: { style: BorderStyle.SINGLE, size: 8, color: '000000' },
    left: { style: BorderStyle.NONE },
    right: { style: BorderStyle.NONE },
    insideHorizontal: { style: BorderStyle.SINGLE, size: 4, color: '999999' },
    insideVertical: { style: BorderStyle.NONE },
  };
  const colW = Math.floor(9360 / headers.length);
  const tableRows = [
    new TableRow({
      tableHeader: true,
      children: headers.map((h) =>
        new TableCell({
          borders,
          width: { size: colW, type: WidthType.DXA },
          shading: { fill: 'F2F2F2', type: ShadingType.CLEAR },
          margins: { top: 60, bottom: 60, left: 100, right: 100 },
          children: [new Paragraph({ children: [run(h, { bold: true, size: 22 })] })],
        })
      ),
    }),
    ...rows.map((r) =>
      new TableRow({
        children: r.map((c) =>
          new TableCell({
            borders,
            width: { size: colW, type: WidthType.DXA },
            margins: { top: 60, bottom: 60, left: 100, right: 100 },
            children: [new Paragraph({ children: [run(String(c ?? ''), { size: 22 })] })],
          })
        ),
      })
    ),
  ];
  return new Table({ width: { size: 9360, type: WidthType.DXA }, columnWidths: headers.map(() => colW), rows: tableRows });
}

const children = [];

// 封面
children.push(new Paragraph({ spacing: { before: 3000 }, alignment: AlignmentType.CENTER, children: [run(projectName, { bold: true, size: 44 })] }));
children.push(new Paragraph({ spacing: { before: 400 }, alignment: AlignmentType.CENTER, children: [run('解决方案', { bold: true, size: 36 })] }));
children.push(new Paragraph({ spacing: { before: 800 }, alignment: AlignmentType.CENTER, children: [run('生成时间：' + now, { size: 22, color: '666666' })] }));
children.push(new Paragraph({ children: [new PageBreak()] }));

// 目录
children.push(heading('目录', 1));
children.push(new TableOfContents('目录', { hyperlink: true, headingStyleRange: '1-2' }));
children.push(new Paragraph({ children: [new PageBreak()] }));

// 一、需求分析
if (ctx.requirements) {
  children.push(heading('一、需求分析', 1));
  const r = ctx.requirements;
  if (r.functional) { children.push(heading('功能需求', 2)); children.push(...bullets(r.functional)); }
  if (r.nonFunctional) { children.push(heading('非功能需求', 2)); children.push(...bullets(r.nonFunctional)); }
  if (r.constraints) { children.push(heading('约束条件', 2)); children.push(...bullets(r.constraints)); }
  if (r.industry) children.push(para('行业场景：' + r.industry, { bold: true }));
}

// 二、产品选型
if (ctx.productSelection && ctx.productSelection.length) {
  children.push(heading('二、产品选型清单', 1));
  const rows = ctx.productSelection.map((p) => [p.productName || '', p.version || '', p.reason || '', p.coverage != null ? (p.coverage + '%') : '']);
  children.push(threeLineTable(['产品名称', '版本', '推荐理由', '需求覆盖率'], rows));
}

// 三、参考案例
if (ctx.caseRecommendations && ctx.caseRecommendations.length) {
  children.push(heading('三、参考案例', 1));
  for (const c of ctx.caseRecommendations) {
    children.push(heading(c.caseName || '案例', 2));
    const lines = [
      c.scenario ? '场景：' + c.scenario : '',
      c.productsUsed ? '使用产品：' + c.productsUsed : '',
      c.keyEffect ? '成效：' + c.keyEffect : '',
      c.matchReason ? '匹配度：' + c.matchReason : (c.matchScore != null ? '匹配度：' + c.matchScore : ''),
    ].filter(Boolean);
    children.push(...lines.map((l) => para(l)));
  }
}

// 四、竞品对比
if (ctx.competitorAnalysis && ctx.competitorAnalysis.length) {
  children.push(heading('四、竞品对比分析', 1));
  for (const c of ctx.competitorAnalysis) {
    children.push(heading(c.competitorName || '竞品', 2));
    const lines = [
      c.ourAdvantage ? '我方优势：' + c.ourAdvantage : '',
      c.ourDisadvantage ? '我方劣势：' + c.ourDisadvantage : '',
      c.recommendation ? '应对建议：' + c.recommendation : '',
    ].filter(Boolean);
    children.push(...lines.map((l) => para(l)));
  }
}

// 五、技术架构
if (ctx.architectureDiagram) {
  children.push(heading('五、技术架构', 1));
  if (ctx.architectureDiagram.description) children.push(para(ctx.architectureDiagram.description));
  if (ctx.architectureDiagram.mermaid) {
    children.push(para('架构图（Mermaid 源码）：', { bold: true, size: 22 }));
    children.push(new Paragraph({ spacing: { after: 160 }, children: [run(ctx.architectureDiagram.mermaid, { size: 18, color: '555555' })] }));
  }
}

// 六、方案大纲
if (ctx.solutionOutline) {
  children.push(heading('六、方案大纲', 1));
  if (ctx.solutionOutline.overview) children.push(para(ctx.solutionOutline.overview));
  if (ctx.solutionOutline.sections) {
    for (const s of ctx.solutionOutline.sections) {
      children.push(heading(s.title || '章节', 2));
      if (s.keyPoints) children.push(para(s.keyPoints));
    }
  }
}

// 七、方案质检
if (ctx.qualityCheck) {
  children.push(heading('七、方案质检', 1));
  const q = ctx.qualityCheck;
  if (q.overallScore != null) children.push(para('整体评分：' + q.overallScore + (q.level ? '（' + q.level + '）' : ''), { bold: true }));
  if (q.dimensions) {
    const rows = q.dimensions.map((d) => [d.dimension || '', d.score != null ? String(d.score) : '', d.comment || '']);
    children.push(threeLineTable(['维度', '评分', '说明'], rows));
  }
  if (q.suggestions) children.push(...bullets(q.suggestions));
}

// 八、解决方案正文
if (ctx.solutionText) {
  children.push(heading('八、解决方案正文', 1));
  // solutionText 可能是 Markdown，按行解析基础结构
  const lines = String(ctx.solutionText).split('\n');
  for (const line of lines) {
    const t = line.trim();
    if (!t) continue;
    if (t.startsWith('# ')) children.push(heading(t.slice(2), 1));
    else if (t.startsWith('## ')) children.push(heading(t.slice(3), 2));
    else if (t.startsWith('- ') || t.startsWith('* ')) children.push(...bullets([t.slice(2)]));
    else if (t.startsWith('|')) {
      // 跳过 markdown 表格行（已在前面结构化呈现）
      continue;
    } else children.push(para(t));
  }
}

const doc = new Document({
  numbering: {
    config: [{ reference: 'bullets', levels: [{ level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 720, hanging: 360 } } } }] }],
  },
  styles: {
    default: { document: { run: { font: FONT, size: 24 } } },
    paragraphStyles: [
      { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true, run: { size: 32, bold: true, font: FONT_HEAD }, paragraph: { spacing: { before: 360, after: 160 }, outlineLevel: 0 } },
      { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true, run: { size: 28, bold: true, font: FONT_HEAD }, paragraph: { spacing: { before: 240, after: 120 }, outlineLevel: 1 } },
    ],
  },
  sections: [
    {
      properties: {
        page: {
          size: { width: 11906, height: 16838 },
          margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 },
        },
      },
      headers: {
        default: new Header({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [run(projectName, { size: 16, color: '999999' })] })] }),
      },
      footers: {
        default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [run('第 ', { size: 16, color: '999999' }), new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: 16, color: '999999' }), run(' 页', { size: 16, color: '999999' })] })] }),
      },
      children,
    },
  ],
});

Packer.toBuffer(doc).then((buffer) => {
  fs.writeFileSync(outputPath, buffer);
  console.log('DOCX generated: ' + outputPath + ' (' + buffer.length + ' bytes)');
}).catch((e) => {
  console.error('DOCX generation failed:', e);
  process.exit(1);
});
