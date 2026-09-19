from pathlib import Path
import re, html
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Image, Table, TableStyle, Preformatted, PageBreak, KeepTogether
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.utils import ImageReader

root=Path(__file__).resolve().parents[1]
out=root/'docs'/'NexaRag-setup-guide.pdf'
styles=getSampleStyleSheet()
styles.add(ParagraphStyle(name='BodyGuide',fontName='Helvetica',fontSize=10,leading=14,spaceAfter=8))
styles.add(ParagraphStyle(name='GuideTitle',fontName='Helvetica-Bold',fontSize=23,leading=28,spaceAfter=14))
styles.add(ParagraphStyle(name='GuideHeading',fontName='Helvetica-Bold',fontSize=17,leading=21,spaceAfter=12))
styles.add(ParagraphStyle(name='GuideCode',fontName='Courier',fontSize=8,leading=11,spaceBefore=5,spaceAfter=10))
styles.add(ParagraphStyle(name='GuideCaption',fontName='Helvetica-Oblique',fontSize=8,leading=11,spaceAfter=12))
styles.add(ParagraphStyle(name='GuideCell',fontName='Helvetica',fontSize=8.5,leading=12,spaceAfter=0))

def markup(t):
    t=html.escape(t)
    t=re.sub(r'\*\*(.+?)\*\*',r'<b>\1</b>',t)
    t=re.sub(r'`([^`]+)`',r'<font name="Courier">\1</font>',t)
    t=re.sub(r'(https://[^\s<]+)',r'<link href="\1" color="#15567B">\1</link>',t)
    return t.replace('“','&quot;').replace('”','&quot;').replace('’',"'")

lines=(root/'docs/setup-guide.md').read_text(encoding='utf8').splitlines()
story=[]; i=0; section=0
while i<len(lines):
    s=lines[i]
    if not s.strip(): i+=1; continue
    if s.startswith('# '): story.append(Paragraph(markup(s[2:]),styles['GuideTitle'])); i+=1; continue
    if s.startswith('## '):
        section+=1
        if section>1: story.append(PageBreak())
        story.append(Paragraph(markup(s[3:]),styles['GuideHeading'])); i+=1; continue
    if s.startswith('```'):
        i+=1; code=[]
        while i<len(lines) and not lines[i].startswith('```'):
            line=lines[i]
            # Wrap long commands visibly without changing the source guide.
            while len(line)>91:
                split=line.rfind(' ',0,90)
                if split<35: break
                code.append(line[:split]); line='  '+line[split+1:]
            code.append(line); i+=1
        story.append(Preformatted('\n'.join(code),styles['GuideCode'])); i+=1; continue
    if s.startswith('|'):
        rows=[]
        while i<len(lines) and lines[i].startswith('|'):
            cells=[x.strip() for x in lines[i].strip('|').split('|')]
            if not all(re.match(r'^[- :]+$',x) for x in cells): rows.append([Paragraph(markup(x),styles['GuideCell']) for x in cells])
            i+=1
        widths=[158,346] if section in [5,9] else [138,366]
        table=Table(rows,colWidths=widths,repeatRows=1,hAlign='LEFT')
        table.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),colors.HexColor('#DFEAF1')),('GRID',(0,0),(-1,-1),.5,colors.HexColor('#D9D9D9')),('VALIGN',(0,0),(-1,-1),'MIDDLE'),('LEFTPADDING',(0,0),(-1,-1),8),('RIGHTPADDING',(0,0),(-1,-1),8),('TOPPADDING',(0,0),(-1,-1),6),('BOTTOMPADDING',(0,0),(-1,-1),6)]))
        story.extend([table,Spacer(1,12)]); continue
    if s.startswith('!['):
        m=re.match(r'!\[(.*?)\]\((.*?)\)',s); path=root/'docs'/m.group(2)
        w,h=ImageReader(str(path)).getSize(); scale=min(504/w,340/h)
        im=Image(str(path),width=w*scale,height=h*scale); im.hAlign='LEFT'
        story.extend([im,Spacer(1,7)]); i+=1; continue
    if s.startswith('*Figure'):
        story.append(Paragraph(markup(s.strip('*')),styles['GuideCaption'])); i+=1; continue
    story.append(Paragraph(markup(s),styles['BodyGuide'])); i+=1

def footer(canvas,doc):
    canvas.setFont('Helvetica',8); canvas.setFillColor(colors.HexColor('#666666'))
    canvas.drawString(54,30,'NexaRag setup guide  |  19 September 2026')
    canvas.drawRightString(558,30,str(doc.page))

SimpleDocTemplate(str(out),pagesize=(612,792),leftMargin=54,rightMargin=54,topMargin=42,bottomMargin=48,title='NexaRag setup and user guide',author='NexaRag').build(story,onFirstPage=footer,onLaterPages=footer)
print(out)

