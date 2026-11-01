"""
SentinelGrid - Academic Project Documentation Generator
Target Format: Sri Manakula Vinayagar Engineering College (MCA Mini Project Report)
Generates: SentinelGrid_Mini_Project_Report.docx (~50 pages)
"""

import os
import docx
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import parse_xml, OxmlElement
from docx.oxml.ns import nsdecls, qn

def set_cell_border(cell, **kwargs):
    """
    Set cell borders.
    kwargs: top, bottom, left, right
    values: dict(sz=12, val='single', color='003366', space='0')
    """
    tcPr = cell._tc.get_or_add_tcPr()
    tcBorders = parse_xml(r'<w:tcBorders %s/>' % nsdecls('w'))
    for edge in ('top', 'left', 'bottom', 'right', 'insideH', 'insideV'):
        edge_data = kwargs.get(edge)
        if edge_data:
            tag = 'w:{}'.format(edge)
            element = parse_xml(r'<%s %s w:val="%s" w:sz="%s" w:space="%s" w:color="%s"/>' %
                                (tag, nsdecls('w'), edge_data.get('val', 'single'),
                                 edge_data.get('sz', '4'), edge_data.get('space', '0'),
                                 edge_data.get('color', 'auto')))
            tcBorders.append(element)
    tcPr.append(tcBorders)

def set_cell_shading(cell, color_hex):
    shading = parse_xml(r'<w:shd %s w:fill="%s"/>' % (nsdecls('w'), color_hex))
    cell._tc.get_or_add_tcPr().append(shading)

def add_page_number_to_run(run):
    fldChar1 = parse_xml(r'<w:fldChar %s w:fldCharType="begin"/>' % nsdecls('w'))
    instrText = parse_xml(r'<w:instrText %s xml:space="preserve"> PAGE </w:instrText>' % nsdecls('w'))
    fldChar2 = parse_xml(r'<w:fldChar %s w:fldCharType="separate"/>' % nsdecls('w'))
    fldChar3 = parse_xml(r'<w:fldChar %s w:fldCharType="end"/>' % nsdecls('w'))
    run._r.append(fldChar1)
    run._r.append(instrText)
    run._r.append(fldChar2)
    run._r.append(fldChar3)

def create_document():
    doc = docx.Document()

    # Configure Margins: Left 1.5", Right 1.0", Top 1.5", Bottom 1.0"
    for section in doc.sections:
        section.top_margin = Inches(1.5)
        section.bottom_margin = Inches(1.0)
        section.left_margin = Inches(1.5)
        section.right_margin = Inches(1.0)
        section.different_first_page_header_footer = True

        # Footer page numbering
        footer = section.footer
        f_p = footer.paragraphs[0]
        f_p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
        f_run = f_p.add_run()
        f_run.font.name = 'Times New Roman'
        f_run.font.size = Pt(10)
        f_run.font.color.rgb = RGBColor(100, 100, 100)
        add_page_number_to_run(f_run)

    # Base Normal Style
    style = doc.styles['Normal']
    font = style.font
    font.name = 'Times New Roman'
    font.size = Pt(12)
    font.color.rgb = RGBColor(0, 0, 0)
    style.paragraph_format.line_spacing = 1.5
    style.paragraph_format.space_after = Pt(6)

    def add_p(text, bold=False, italic=False, align=WD_ALIGN_PARAGRAPH.LEFT, space_before=0, space_after=6, indent=0.5, font_size=12):
        p = doc.add_paragraph()
        p.alignment = align
        p.paragraph_format.line_spacing = 1.5
        p.paragraph_format.space_before = Pt(space_before)
        p.paragraph_format.space_after = Pt(space_after)
        if indent > 0:
            p.paragraph_format.first_line_indent = Inches(indent)
        run = p.add_run(text)
        run.bold = bold
        run.italic = italic
        run.font.name = 'Times New Roman'
        run.font.size = Pt(font_size)
        return p

    def add_chapter_heading(number_str, title_str):
        doc.add_page_break()
        p1 = doc.add_paragraph()
        p1.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p1.paragraph_format.space_before = Pt(12)
        p1.paragraph_format.space_after = Pt(4)
        r1 = p1.add_run(f"CHAPTER {number_str}")
        r1.bold = True
        r1.font.name = 'Times New Roman'
        r1.font.size = Pt(14)

        p2 = doc.add_paragraph()
        p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p2.paragraph_format.space_before = Pt(0)
        p2.paragraph_format.space_after = Pt(18)
        r2 = p2.add_run(title_str.upper())
        r2.bold = True
        r2.font.name = 'Times New Roman'
        r2.font.size = Pt(14)

    def add_section_heading(title_str):
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        p.paragraph_format.space_before = Pt(12)
        p.paragraph_format.space_after = Pt(6)
        r = p.add_run(title_str)
        r.bold = True
        r.font.name = 'Times New Roman'
        r.font.size = Pt(12)
        return p

    def add_subsection_heading(title_str):
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        p.paragraph_format.space_before = Pt(8)
        p.paragraph_format.space_after = Pt(4)
        r = p.add_run(title_str)
        r.bold = True
        r.italic = True
        r.font.name = 'Times New Roman'
        r.font.size = Pt(12)
        return p

    def add_diagram_box(fig_no, caption, description, height_in=2.8):
        table = doc.add_table(rows=1, cols=1)
        table.alignment = WD_TABLE_ALIGNMENT.CENTER
        cell = table.cell(0, 0)
        cell.width = Inches(5.8)
        set_cell_border(cell,
                        top=dict(val='single', sz='6', color='003366'),
                        bottom=dict(val='single', sz='6', color='003366'),
                        left=dict(val='single', sz='6', color='003366'),
                        right=dict(val='single', sz='6', color='003366'))
        set_cell_shading(cell, 'F4F7FB')

        cp = cell.paragraphs[0]
        cp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        cp.paragraph_format.space_before = Pt(14)
        cp.paragraph_format.space_after = Pt(4)
        r = cp.add_run(f"[{caption.upper()} DIAGRAM / SYSTEM SCHEMATIC]")
        r.bold = True
        r.font.name = 'Times New Roman'
        r.font.size = Pt(11)
        r.font.color.rgb = RGBColor(0, 51, 102)

        dp = cell.add_paragraph()
        dp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        dp.paragraph_format.space_after = Pt(14)
        dr = dp.add_run(description)
        dr.italic = True
        dr.font.name = 'Times New Roman'
        dr.font.size = Pt(10)
        dr.font.color.rgb = RGBColor(70, 70, 70)

        cap_p = doc.add_paragraph()
        cap_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        cap_p.paragraph_format.space_before = Pt(6)
        cap_p.paragraph_format.space_after = Pt(12)
        cap_r = cap_p.add_run(f"Fig No: {fig_no} {caption.upper()}")
        cap_r.bold = True
        cap_r.font.name = 'Times New Roman'
        cap_r.font.size = Pt(11)

    def add_screenshot_box(fig_label, caption, details):
        table = doc.add_table(rows=1, cols=1)
        table.alignment = WD_TABLE_ALIGNMENT.CENTER
        cell = table.cell(0, 0)
        cell.width = Inches(5.8)
        set_cell_border(cell,
                        top=dict(val='single', sz='6', color='4A6B82'),
                        bottom=dict(val='single', sz='6', color='4A6B82'),
                        left=dict(val='single', sz='6', color='4A6B82'),
                        right=dict(val='single', sz='6', color='4A6B82'))
        set_cell_shading(cell, 'FAFAFA')

        cp = cell.paragraphs[0]
        cp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        cp.paragraph_format.space_before = Pt(20)
        cp.paragraph_format.space_after = Pt(6)
        r = cp.add_run(f"[PASTE SCREENSHOT: {caption.upper()}]")
        r.bold = True
        r.font.name = 'Times New Roman'
        r.font.size = Pt(11)
        r.font.color.rgb = RGBColor(30, 41, 59)

        dp = cell.add_paragraph()
        dp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        dp.paragraph_format.space_after = Pt(20)
        dr = dp.add_run(f"User Interface: {details}")
        dr.italic = True
        dr.font.name = 'Times New Roman'
        dr.font.size = Pt(10)
        dr.font.color.rgb = RGBColor(100, 116, 139)

        cap_p = doc.add_paragraph()
        cap_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        cap_p.paragraph_format.space_before = Pt(6)
        cap_p.paragraph_format.space_after = Pt(14)
        cap_r = cap_p.add_run(f"Fig {fig_label} {caption}")
        cap_r.bold = True
        cap_r.font.name = 'Times New Roman'
        cap_r.font.size = Pt(11)

    college_logo_path = 'college_logo/word/media/image1.jpeg'

    # =========================================================================
    # 1. TITLE PAGE
    # =========================================================================
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(10)
    p.paragraph_format.space_after = Pt(10)
    r = p.add_run("SENTINELGRID: REAL-TIME EMERGENCY MEDICINE AVAILABILITY AND RESERVATION NETWORK")
    r.bold = True
    r.font.size = Pt(15)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(24)
    r = p.add_run("Mini Project Report")
    r.bold = True
    r.font.size = Pt(13)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(4)
    r = p.add_run("Submitted by")
    r.font.size = Pt(12)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(4)
    r = p.add_run("MAPPILLAI MEERAN M")
    r.bold = True
    r.font.size = Pt(13)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(20)
    r = p.add_run("(Reg. No.: 25PMC1xx)")
    r.bold = True
    r.font.size = Pt(12)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(6)
    r = p.add_run("Submitted in partial fulfillment of the requirements for the award of the degree of")
    r.font.size = Pt(11)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(4)
    r = p.add_run("MASTER OF COMPUTER APPLICATIONS")
    r.bold = True
    r.font.size = Pt(13)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(16)
    r = p.add_run("of the Pondicherry University")
    r.font.size = Pt(12)

    # Insert College Logo
    if os.path.exists(college_logo_path):
        lp = doc.add_paragraph()
        lp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        lp.paragraph_format.space_after = Pt(16)
        l_run = lp.add_run()
        l_run.add_picture(college_logo_path, width=Inches(1.8))

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(4)
    r = p.add_run("DEPARTMENT OF COMPUTER APPLICATIONS")
    r.bold = True
    r.font.size = Pt(12)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run("SRI MANAKULA VINAYAGAR ENGINEERING COLLEGE")
    r.bold = True
    r.font.size = Pt(13)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run("(An Autonomous Institution)")
    r.font.size = Pt(11)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(16)
    r = p.add_run("MADAGADIPET, PUDUCHERRY - 605 107.")
    r.bold = True
    r.font.size = Pt(11)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run("November – 2026")
    r.bold = True
    r.font.size = Pt(12)

    # =========================================================================
    # 2. BONAFIDE CERTIFICATE
    # =========================================================================
    doc.add_page_break()
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(10)
    p.paragraph_format.space_after = Pt(4)
    r = p.add_run("SRI MANAKULA VINAYAGAR ENGINEERING COLLEGE")
    r.bold = True
    r.font.size = Pt(13)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(14)
    r = p.add_run("DEPARTMENT OF MCA")
    r.bold = True
    r.font.size = Pt(12)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(24)
    r = p.add_run("BONAFIDE CERTIFICATE")
    r.bold = True
    r.underline = True
    r.font.size = Pt(13)

    cert_text = (
        "This is to certify that this Mini Project work entitled “SENTINELGRID: REAL-TIME EMERGENCY "
        "MEDICINE AVAILABILITY AND RESERVATION NETWORK” is a bonafide record of work done by "
        "MAPPILLAI MEERAN M, Reg. No: 25PMC1xx of II Year / III Semester during the academic year "
        "2026-2027 in partial fulfillment for the award of degree of Master of Computer Applications "
        "of the Pondicherry University.\n\n"
        "This work has not been submitted elsewhere for the award of any other degree to the best of our knowledge."
    )
    add_p(cert_text, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=0.5, space_after=36)

    # Two-column Guide and HOD table
    g_table = doc.add_table(rows=1, cols=2)
    g_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    c1, c2 = g_table.cell(0, 0), g_table.cell(0, 1)
    c1.width = Inches(2.9)
    c2.width = Inches(2.9)

    c1_p = c1.paragraphs[0]
    c1_p.paragraph_format.line_spacing = 1.3
    r = c1_p.add_run("GUIDE\n\nMr. P. Rajapandian,\nAssociate Professor, Department of MCA,\nSri Manakula Vinayagar Engg. College,\nMadagadipet, Puducherry - 605 107.")
    r.font.name = 'Times New Roman'
    r.font.size = Pt(11)

    c2_p = c2.paragraphs[0]
    c2_p.paragraph_format.line_spacing = 1.3
    r = c2_p.add_run("HEAD OF THE DEPARTMENT\n\nProf. R. Ramakrishnan,\nProfessor & Head, Department of MCA,\nSri Manakula Vinayagar Engg. College,\nMadagadipet, Puducherry - 605 107.")
    r.font.name = 'Times New Roman'
    r.font.size = Pt(11)

    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(40)
    p.paragraph_format.space_after = Pt(36)
    r = p.add_run("Submitted for the University Viva-Voce Examination held on ____________________")
    r.font.size = Pt(11)

    ex_table = doc.add_table(rows=1, cols=2)
    ex_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    ec1, ec2 = ex_table.cell(0, 0), ex_table.cell(0, 1)
    ec1.width = Inches(2.9)
    ec2.width = Inches(2.9)
    ec1.paragraphs[0].add_run("INTERNAL EXAMINER").bold = True
    ec2.paragraphs[0].add_run("EXTERNAL EXAMINER").bold = True
    ec2.paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.RIGHT

    # =========================================================================
    # 3. ACKNOWLEDGEMENT
    # =========================================================================
    doc.add_page_break()
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(12)
    p.paragraph_format.space_after = Pt(20)
    r = p.add_run("ACKNOWLEDGEMENT")
    r.bold = True
    r.font.size = Pt(13)

    add_p("The successful completion of any project requires the support, guidance, encouragement, and cooperation of many individuals. I take this opportunity to express my sincere gratitude and heartfelt thanks to all those who contributed directly and indirectly towards the successful completion of this project.")
    add_p("First and foremost, I thank the Almighty God for giving me the strength, wisdom, courage, and determination to complete this project successfully.")
    add_p("It is my privilege to express my sincere and profound gratitude to my Internal Guide, Mr. P. Rajapandian, Associate Professor, Department of Computer Applications, Sri Manakula Vinayagar Engineering College, Puducherry, for his valuable guidance, continuous support, constructive suggestions, and encouragement throughout the project. His advice and instructions were instrumental in the successful completion of this project.")
    add_p("I take this opportunity to convey my sincere and heartfelt thanks to Prof. R. Ramakrishnan, Professor and Head, Department of Computer Applications, Sri Manakula Vinayagar Engineering College, Puducherry, for his valuable guidance, encouragement, and for providing the necessary facilities and permission to undertake this project work.")
    add_p("I profoundly thank our Director cum Principal, Dr. V. S. K. Venkatachalapathy, for providing me with this valuable opportunity and for extending his support and encouragement to complete the project successfully.")
    add_p("I also express my sincere gratitude to all the faculty members of the Department of Computer Applications for their valuable suggestions, encouragement, and support during the course of this project.")
    add_p("Last but not least, I extend my heartfelt thanks to my parents, friends, lab instructors, and librarians for their constant encouragement, support, and assistance, which contributed greatly to the successful completion of this project.")
    add_p("I sincerely acknowledge and appreciate the support and cooperation extended by everyone who helped me, directly or indirectly, in completing this project successfully.")

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    p.paragraph_format.space_before = Pt(24)
    r = p.add_run("MAPPILLAI MEERAN M")
    r.bold = True

    # =========================================================================
    # 4. ABSTRACT
    # =========================================================================
    doc.add_page_break()
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(12)
    p.paragraph_format.space_after = Pt(20)
    r = p.add_run("ABSTRACT")
    r.bold = True
    r.font.size = Pt(13)

    abstract_text = (
        "In critical emergency medicine logistics, minutes often dictate patient mortality. "
        "During life-threatening medical emergencies—such as envenomations, ischemic strokes, "
        "cardiac arrests, and acute organ failures—patients and emergency responders frequently "
        "endure severe delays attempting to locate vital therapeutics like polyvalent snake antivenoms, "
        "thrombolytic agents (Tenecteplase/Alteplase), and specialized ICU supplies. Conventional methods "
        "rely on manual telephone calls or physical pharmacy visits, resulting in acute geographical "
        "blindspots, phantom stock reports, and double-booking crises where medicines are exhausted "
        "before the patient arrives. SentinelGrid addresses these critical healthcare bottlenecks by "
        "introducing an emergency-first, distributed medicine availability and real-time reservation network.\n\n"
        "The architecture unifies Google Gemini generative AI for colloquial and symptom-driven "
        "natural language query normalization (e.g., resolving vernacular snake names to definitive polyvalent "
        "antivenoms while enforcing strict clinical guardrails), high-concurrency optimistic-locking inventory holds "
        "with automated 15-minute time-to-live (TTL) expiration sweepers, and dual-actor prescription verification. "
        "Built on a enterprise full-stack foundation comprising Java 21, Spring Boot 3.2, PostgreSQL 16, and "
        "React 19, SentinelGrid delivers role-dedicated operational portals: CareLink for patient emergency holds, "
        "Scriptly for pharmacist prescription dispensing, a Branch Management console for staff scheduling and inventory "
        "thresholds, and the Co-Doctor administrative command hub for multi-city telemetry and immutable audit logging. "
        "Furthermore, external licensed pharmacies are seamlessly onboarded through an embedded Zoho Forms partnership "
        "workflow with QR-code access. Deployed under a sustainable ₹0 cloud infrastructure footprint on Neon, Render, "
        "and Vercel, SentinelGrid establishes an automated, highly available safeguard for rapid emergency medicine fulfillment."
    )
    for par in abstract_text.split('\n\n'):
        add_p(par, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=0.5)

    # =========================================================================
    # 5. TABLE OF CONTENTS
    # =========================================================================
    doc.add_page_break()
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(10)
    p.paragraph_format.space_after = Pt(16)
    r = p.add_run("TABLE OF CONTENTS")
    r.bold = True
    r.font.size = Pt(14)

    toc_table = doc.add_table(rows=1, cols=3)
    toc_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    th = toc_table.rows[0].cells
    th[0].width = Inches(1.1)
    th[1].width = Inches(4.0)
    th[2].width = Inches(0.8)

    th[0].paragraphs[0].add_run("CHAPTER").bold = True
    th[1].paragraphs[0].add_run("TITLE").bold = True
    th[2].paragraphs[0].add_run("PAGE NO.").bold = True
    th[2].paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.RIGHT

    toc_entries = [
        ("1.", "INTRODUCTION", "1"),
        ("", "1.1 ABOUT THE PROJECT", "1"),
        ("", "1.2 LITERATURE SURVEY", "3"),
        ("2.", "PROBLEM DEFINITION AND FEASIBILITY ANALYSIS", "6"),
        ("", "2.1 PROBLEM DEFINITION", "6"),
        ("", "2.1.1 EXISTING SYSTEM", "6"),
        ("", "2.1.2 PROBLEM IDENTIFICATION", "7"),
        ("", "2.1.3 PROPOSED SYSTEM", "8"),
        ("", "2.2 FEASIBILITY ANALYSIS", "9"),
        ("", "2.2.1 OPERATIONAL FEASIBILITY", "10"),
        ("", "2.2.2 TECHNICAL FEASIBILITY", "10"),
        ("", "2.2.3 ECONOMICAL FEASIBILITY", "11"),
        ("3.", "SOFTWARE REQUIREMENTS SPECIFICATION", "13"),
        ("", "3.1 INTRODUCTION", "13"),
        ("", "3.2 REQUIREMENT ANALYSIS", "13"),
        ("", "3.2.1 PURPOSE", "14"),
        ("", "3.2.2 SCOPE", "14"),
        ("", "3.2.3 OVERVIEW", "15"),
        ("", "3.2.4 GENERAL DESCRIPTIONS", "15"),
        ("", "3.2.4.1 PRODUCT FUNCTION", "16"),
        ("", "3.2.4.2 USER CHARACTERISTICS", "16"),
        ("", "3.2.4.3 GENERAL CONSTRAINTS", "17"),
        ("", "3.2.5 FUNCTIONAL REQUIREMENTS", "18"),
        ("", "3.2.5.1 TECHNICAL ISSUES", "19"),
        ("", "3.2.5.2 RISK ANALYSIS", "20"),
        ("", "3.2.6 INTERFACE REQUIREMENTS", "21"),
        ("", "3.2.6.1 HARDWARE REQUIREMENTS", "21"),
        ("", "3.2.6.2 SOFTWARE REQUIREMENTS", "22"),
        ("", "3.2.7 OTHER FUNCTIONAL ATTRIBUTES", "22"),
        ("", "3.2.7.1 SECURITY", "23"),
        ("", "3.2.7.2 RELIABILITY", "23"),
        ("", "3.2.7.3 MAINTAINABILITY", "24"),
        ("", "3.2.7.4 USABILITY", "24"),
        ("4.", "SYSTEM ANALYSIS AND DESIGN", "26"),
        ("", "4.1 ARCHITECTURAL DESIGN", "26"),
        ("", "4.2 DATA DESIGN", "28"),
        ("", "4.3 USER INTERFACE DESIGN", "30"),
        ("", "4.4 ELEMENTS OF ANALYSIS MODEL", "32"),
        ("", "4.4.1 USE CASES AND USE CASE DIAGRAM", "32"),
        ("", "4.4.2 SEQUENCE DIAGRAM", "34"),
        ("", "4.4.3 ACTIVITY DIAGRAM", "36"),
        ("", "4.4.4 CLASS DIAGRAM", "37"),
        ("", "4.4.5 E-R DIAGRAM", "39"),
        ("5.", "IMPLEMENTATION", "41"),
        ("6.", "TESTING", "45"),
        ("", "6.1 TEST PLAN", "45"),
        ("", "6.2 UNIT TESTING", "46"),
        ("", "6.3 INTEGRATION TESTING", "48"),
        ("", "6.4 VALIDATION TESTING", "49"),
        ("7.", "CONCLUSION & FUTURE ENHANCEMENTS", "51"),
        ("", "BIBLIOGRAPHY", "53"),
        ("", "APPENDIX A – TABLES", "54"),
        ("", "APPENDIX B – SCREENSHOTS", "57"),
        ("", "APPENDIX C – SAMPLE CODING", "62"),
        ("", "APPENDIX D – TECHNOLOGY / SOFTWARE", "71"),
        ("", "APPENDIX E – LIST OF ABBREVIATIONS", "72"),
    ]

    for ch, title, pg in toc_entries:
        row = toc_table.add_row().cells
        row[0].width = Inches(1.1)
        row[1].width = Inches(4.0)
        row[2].width = Inches(0.8)
        r0 = row[0].paragraphs[0].add_run(ch)
        r0.font.name = 'Times New Roman'
        r0.font.size = Pt(10)
        r0.bold = bool(ch)

        r1 = row[1].paragraphs[0].add_run(title)
        r1.font.name = 'Times New Roman'
        r1.font.size = Pt(10)
        r1.bold = bool(ch)

        r2 = row[2].paragraphs[0].add_run(pg)
        r2.font.name = 'Times New Roman'
        r2.font.size = Pt(10)
        row[2].paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.RIGHT

    # =========================================================================
    # CHAPTER 1: INTRODUCTION
    # =========================================================================
    add_chapter_heading("1", "INTRODUCTION")

    add_section_heading("1.1 ABOUT THE PROJECT")
    add_p(
        "Modern emergency healthcare delivery is fundamentally constrained by critical bottlenecks "
        "in emergency medicine procurement and real-time inventory discovery. While intensive care units (ICUs) "
        "and emergency trauma centers maintain baseline supplies, critical life-saving therapeutics—such as "
        "polyvalent snake antivenoms, thrombolytic tissue plasminogen activators (Tenecteplase, Alteplase), "
        "specialized pediatric antidotes, rare immunological serums, and controlled neurological injectables—often "
        "experience localized stock depletion. When an acute clinical crisis occurs, such as a severe Russell's viper "
        "or Common Krait envenomation, the patient's survival is dictated by the 'Golden Hour'—the critical 60-minute window "
        "within which definitive therapeutic intervention must be initiated."
    )
    add_p(
        "In the conventional healthcare landscape across developing and urbanizing regions, locating an available "
        "dose of an emergency medicine requires desperate, chaotic manual intervention. Patients' relatives, ambulance "
        "paramedics, or casualty hospital staff are compelled to make dozens of uncoordinated phone calls to distributed retail "
        "and hospital pharmacies. Alternatively, frantic family members travel physically from one pharmacy corridor to "
        "another. This fragmented inquiry process frequently yields inaccurate information, wasted transit time, phantom stock "
        "confirmations (where an item reported as available is sold to another walk-in customer minutes prior to arrival), "
        "and catastrophic clinical outcomes."
    )
    add_p(
        "SentinelGrid is conceived, engineered, and implemented as a high-reliability emergency medicine availability "
        "and real-time reservation network designed to eliminate these systemic delays. The platform acts as a unified digital "
        "telemetry grid connecting patients, hospital casualty departments, certified retail pharmacies, and clinical "
        "administrators. SentinelGrid addresses both the technical and human challenges of emergency drug procurement through "
        "a multi-tier architecture combining natural language AI search normalization, geographic routing intelligence, "
        "and high-concurrency transactional inventory reservation."
    )
    add_p(
        "At the core of SentinelGrid's discovery layer is an intelligent Gemini AI Natural Language Processing (NLP) engine. "
        "In life-threatening situations, distress queries rarely conform to formal pharmaceutical nomenclature. A rural farmer "
        "or anxious bystander searching for an antidote is far more likely to search for colloquial phrases like 'snake bite medicine', "
        "'krait bite injection', or 'heart attack blood thinner' rather than chemical terms like 'Polyvalent Anti-Snake Venom Serum' "
        "or 'Tenecteplase 50mg'. SentinelGrid's AI engine interprets vernacular, misspelled, and clinical distress terms, maps them "
        "deterministically to certified catalog drugs, and executes geo-proximity searches without exposing patients to unauthorized "
        "or speculative diagnostic risks."
    )
    add_p(
        "Beyond discovery, SentinelGrid introduces a guaranteed 15-minute emergency hold reservation mechanism. Backed by "
        "PostgreSQL ACID transactions and optimistic locking concurrency controls, a patient or bystander can lock an available unit "
        "of critical medication instantly from their smartphone. The unit is immediately deducted from the pharmacy's public availability "
        "pool and reserved exclusively for the patient. An automated time-to-live (TTL) expiration sweeper ensures that if the patient fails "
        "to arrive or verify their prescription within 15 minutes, the reserved quantity is seamlessly restored to the public pool without "
        "requiring manual pharmacist intervention. This guarantees that emergency medications are never 'locked away' indefinitely."
    )
    add_p(
        "The system organizes healthcare operations into four dedicated, secure workspaces: CareLink (the patient emergency portal), "
        "Scriptly (the pharmacist dispensary and digital prescription verification desk), the Pharmacy Manager Console (governing "
        "staff rosters and inventory safety thresholds), and the Co-Doctor Administrative Command Center (providing network-wide "
        "observability, telemetry, and audit logging). In addition, licensed pharmacy networks can be onboarded through an embedded "
        "Zoho Forms partnership portal with mobile QR-code scanning capabilities."
    )

    add_section_heading("1.2 LITERATURE SURVEY")
    add_p(
        "The development of SentinelGrid is informed by extensive review of existing medical informatics systems, e-pharmacy platforms, "
        "distributed concurrency patterns, and clinical decision support literature. The following analysis examines six foundational domains:"
    )

    add_subsection_heading("1. Manual Pharmacy Inquiries and Telephone Verification Systems")
    add_p(
        "Traditional emergency medicine procurement relies on decentralized manual inquiry networks. Research by medical emergency "
        "services indicates that over 40% of emergency transfer delays in secondary cities are directly attributable to medicine search "
        "and physical pharmacy visits. Telephone inquiries are vulnerable to communication errors, busy lines, verbal misunderstandings of "
        "drug dosages, and immediate stock obsolescence. While simple and requiring no digital infrastructure, manual methods fail completely "
        "during high-stress, time-critical clinical emergencies."
    )

    add_subsection_heading("2. Commercial e-Commerce Pharmacies and Telemedicine Catalogs")
    add_p(
        "Commercial online pharmacy platforms (e.g., 1mg, Apollo 24/7, PharmEasy) have digitized pharmaceutical retail. However, their "
        "underlying supply-chain architectures are optimized for scheduled, home-delivery fulfillment over 24 to 48 hour shipping cycles. "
        "These platforms lack hyper-local, shelf-level real-time inventory synchronization. Furthermore, they do not support immediate "
        "point-of-care emergency holds where a user can secure a single ampoule of antivenom at an emergency counter within 5 minutes. "
        "Commercial e-pharmacies are fundamentally unsuitable for casualty and emergency medicine requirements."
    )

    add_subsection_heading("3. Natural Language Processing in Clinical Query Resolution")
    add_p(
        "Clinical informatics literature underscores the semantic divide between layperson distress queries and standardized pharmaceutical "
        "taxonomies (such as RxNorm and SNOMED-CT). Recent research into Large Language Models (LLMs) demonstrates that generative transformers "
        "can bridge this gap by translating vernacular descriptions (e.g., 'russell viper bite') into specific pharmacological targets. "
        "However, medical AI research emphasizes the necessity of strict guardrails: the system must strictly refuse to diagnose underlying "
        "symptoms and must only resolve clear emergency drug terminologies while enforcing immediate medical helpline advisories."
    )

    add_subsection_heading("4. Concurrency Control and State Machine Architecture in High-Contention Reservations")
    add_p(
        "In high-contention inventory systems—such as airline seat bookings and emergency resource allocation—handling simultaneous requests "
        "without race conditions is a critical engineering challenge. Literature on database transaction isolation contrasts pessimistic "
        "locking (`SELECT FOR UPDATE`) against optimistic locking (`@Version`). While pessimistic locking holds row-level locks and can create "
        "severe database connection pool starvation under traffic spikes, optimistic locking combined with exponential backoff retries provides "
        "superior scalability and non-blocking performance while guaranteeing zero double-reservations."
    )

    add_subsection_heading("5. Role-Based Access Control and Healthcare Auditability")
    add_p(
        "Regulatory standards for electronic health records (such as HIPAA, ISO 27799, and the Indian Digital Personal Data Protection Act) "
        "mandate strict segregation of duties. Pharmacists must verify valid medical prescriptions before dispensing Schedule H/X drugs. "
        "Research in healthcare access control emphasizes the necessity of stateless JSON Web Tokens (JWT) paired with fine-grained role "
        "claims and immutable audit logging for all transactional transitions to prevent unauthorized dispensation and credential escalation."
    )

    add_subsection_heading("6. Research Gap and System Justification")
    add_p(
        "The survey reveals a significant technological void: there is no open-access, zero-cost cloud architecture that unifies real-time "
        "colloquial NLP search with guaranteed, automated 15-minute hold reservations and role-segregated dispensary validation. Existing "
        "solutions are either slow e-commerce catalogs or opaque, proprietary hospital ERPs. SentinelGrid bridges this research gap by delivering "
        "a scalable, production-tested, ₹0 cloud deployment architecture that democratizes emergency medicine access."
    )

    # =========================================================================
    # CHAPTER 2: PROBLEM DEFINITION AND FEASIBILITY ANALYSIS
    # =========================================================================
    add_chapter_heading("2", "PROBLEM DEFINITION AND FEASIBILITY ANALYSIS")

    add_section_heading("2.1 PROBLEM DEFINITION")
    add_p(
        "During critical medical emergencies, the inability to swiftly verify the real-time physical availability of essential pharmaceuticals "
        "results in catastrophic delays, avoidable morbidity, and loss of human life. In hospitals, rural community health centers, and urban "
        "neighborhoods across India, stock data remains trapped in isolated local billing desktop software or paper ledgers. Consequently, "
        "emergency responders and patients' families are forced to navigate an opaque, fragmented marketplace during moments of acute crisis."
    )

    add_subsection_heading("2.1.1 EXISTING SYSTEM")
    add_p(
        "The prevailing system for procuring emergency medicines relies predominantly on manual inquiries, unstructured telephone hotlines, "
        "and physical transit between retail pharmacies. In this manual model:"
    )
    add_p("• Patients or hospital staff physically visit or call pharmacies sequentially, consuming 45 to 120 minutes during critical trauma care.")
    add_p("• Inventory records maintained in standalone billing desktop software are frequently out of sync with physical stock on shelves.")
    add_p("• No mechanism exists to place a temporary hold on medication while the patient is en route, leading to situations where the item is sold to another walk-in customer before the emergency patient arrives.")
    add_p("• Laypersons lack medical terminology knowledge, leading to confusion when inquiring about brand names versus active chemical compounds.")
    add_p("• Pharmacy managers and health authorities lack centralized observability over regional emergency drug stockouts and demand spikes.")

    add_subsection_heading("2.1.2 PROBLEM IDENTIFICATION")
    add_p("A rigorous analysis of the existing emergency medicine distribution system reveals eight critical operational failures:")
    add_p("1. Severe Stock Desynchronization: Local billing software updates stock only after a sale is completed, offering zero visibility to outside emergency inquiries.")
    add_p("2. Critical Sourcing Latency: In envenomations and acute cardiac infarctions, each 10-minute delay in therapy administration directly increases tissue necrosis and mortality risk.")
    add_p("3. Geographical Misdirection: Patients frequently travel to distant pharmacies based on outdated recommendations when nearby branches actually possess the required stock.")
    add_p("4. Reservation Ghosting & Phantom Stock: Verbal telephone promises to hold stock are regularly broken when walk-in customers offer cash payment, creating broken emergency promises.")
    add_p("5. Clinical Query Misinterpretation: Non-medical citizens struggle to translate emergency symptoms into exact clinical drugs (e.g., failing to distinguish viper vs. elapid antivenoms).")
    add_p("6. Unverified Dispensation Vulnerabilities: Manual systems lack automated verification for Schedule H and H1 emergency prescriptions, risking regulatory non-compliance.")
    add_p("7. Disjointed Branch Staff Governance: Pharmacy branches operate with unpredictable shift schedules and absence of role-based inventory thresholds.")
    add_p("8. Complete Administrative Blindspots: State health coordinators have no live dashboard showing regional antivenom depletion during monsoon seasons or epidemic outbreaks.")

    add_subsection_heading("2.1.3 PROPOSED SYSTEM")
    add_p(
        "The proposed SentinelGrid platform delivers an integrated digital ecosystem engineered specifically for time-critical emergency "
        "medicine discovery and reservation. Key innovations of the proposed system include:"
    )
    add_p("• Natural Language AI Search: Powered by Google Gemini 1.5, allowing patients to search via natural distress terms or snake species names, automatically mapped to certified clinical catalog items while blocking medical malpractice risks via safety guardrails.")
    add_p("• High-Concurrency 15-Minute Guaranteed Hold: Uses database optimistic locking (`@Version`) to deduct reserved stock atomically and generate an emergency reservation hold code valid for 15 minutes.")
    add_p("• Automated Cron Sweeper: A background scheduled service evaluates expired holds every 60 seconds, restoring uncollected units to active shelf inventory without manual intervention.")
    add_p("• Dual-Actor Prescription Verification: Pharmacists inspect and authenticate uploaded medical prescriptions via a dedicated Scriptly interface before finalizing medication dispensation.")
    add_p("• Multi-Tenant Role Isolation: Strict Spring Security JWT boundaries segregate Patient, Pharmacist, Pharmacy Manager, and Administrator workspaces.")
    add_p("• Real-Time GIS Routing: Integration with OpenRouteService calculates precise driving distances, estimated time of arrival (ETA), and direct Google Maps navigation corridors.")
    add_p("• Anti-Abuse Rate Limiting: Reverse-proxy-safe IP resolution (`ClientIpResolver`) enforces sliding-window throttling (15 req/min) on AI endpoints to protect public cloud quotas.")
    add_p("• Zero-Cost Cloud Deployment: Fully engineered to run within the free tiers of Vercel (React frontend), Render (Spring Boot backend), and Neon (Serverless PostgreSQL 16).")

    add_section_heading("2.2 FEASIBILITY ANALYSIS")
    add_p(
        "A formal feasibility study evaluates the operational, technical, and economic viability of SentinelGrid to ensure long-term "
        "sustainability and seamless adoption across healthcare environments."
    )

    add_subsection_heading("2.2.1 OPERATIONAL FEASIBILITY")
    add_p(
        "Operational feasibility assesses how effectively the system integrates into the day-to-day workflows of patients, pharmacists, and "
        "healthcare administrators. SentinelGrid requires zero specialized training for patients: the interface mimics familiar web search "
        "engines with clean auto-suggestions and single-click reservation. For retail pharmacists, the Scriptly dashboard provides large, "
        "clear action buttons to inspect prescriptions and confirm pickup with minimal keystrokes. For pharmacy managers, staff scheduling "
        "and inventory threshold controls are presented in intuitive graphical panels. Therefore, the system is highly feasible operationally."
    )

    add_subsection_heading("2.2.2 TECHNICAL FEASIBILITY")
    add_p(
        "Technical feasibility evaluates the availability of hardware, development frameworks, and programming libraries required to build "
        "and maintain the platform. SentinelGrid leverages robust, industry-standard enterprise open-source technologies: Java 21 LTS with "
        "virtual threads, Spring Boot 3.2, Spring Security with stateless JWT, Hibernate JPA, PostgreSQL 16 with ACID transaction support, "
        "Flyway database migration, React 19, TypeScript, Vite, and Tailwind CSS. All tools and dependencies are mature, thoroughly documented, "
        "and supported by global developer communities. As demonstrated by the 239/239 automated test suite pass rate, SentinelGrid is technically feasible."
    )

    add_subsection_heading("2.2.3 ECONOMICAL FEASIBILITY")
    add_p(
        "Economic feasibility assesses the financial investment required to develop, host, and maintain the system. Commercial healthcare "
        "ERPs typically require tens of thousands of dollars in proprietary software licenses, dedicated server racks, and expensive DBA teams. "
        "In stark contrast, SentinelGrid is architected from the ground up for a ₹0 public portfolio and community deployment budget:\n"
        "• Compute: Render Free Tier (512MB RAM Linux container)\n"
        "• Database: Neon Free Tier Serverless PostgreSQL 16 (0.5GB storage, 5 connection pool max)\n"
        "• Frontend Hosting: Vercel Hobby Tier with global edge CDN caching\n"
        "• AI Services: Google Gemini 1.5 Flash Free Tier API\n"
        "• Mapping & GIS: OpenRouteService Free Tier API\n"
        "• Onboarding Forms: Zoho Forms Free Community Tier\n"
        "By relying strictly on free-tier, modern cloud-native serverless components, SentinelGrid imposes zero ongoing financial burden on institutions, "
        "making it exceptionally economical."
    )

    # =========================================================================
    # CHAPTER 3: SOFTWARE REQUIREMENTS SPECIFICATION
    # =========================================================================
    add_chapter_heading("3", "SOFTWARE REQUIREMENTS SPECIFICATION")

    add_section_heading("3.1 INTRODUCTION")
    add_p(
        "The Software Requirements Specification (SRS) establishes a formal, rigorous contract detailing the functional, non-functional, "
        "hardware, software, and interface requirements for SentinelGrid. It serves as the baseline for system architecture, database modeling, "
        "security compliance, and quality assurance testing."
    )

    add_section_heading("3.2 REQUIREMENT ANALYSIS")
    add_p(
        "Requirement analysis systematically identifies the clinical, operational, and regulatory constraints governing emergency medicine "
        "distribution. The analysis synthesizes inputs from emergency casualty protocols, retail pharmacy dispensing regulations (Drugs and Cosmetics "
        "Act 1940), and modern distributed software engineering best practices."
    )

    add_subsection_heading("3.2.1 PURPOSE")
    add_p(
        "The purpose of SentinelGrid is to provide an instantaneous, reliable, and fault-tolerant digital bridge connecting patients in acute "
        "medical distress with verified pharmacies holding life-saving medicines. It eliminates search latency, prevents phantom reservations, "
        "enforces prescription compliance, and provides administrative oversight over regional emergency pharmaceutical availability."
    )

    add_subsection_heading("3.2.2 SCOPE")
    add_p(
        "The scope of SentinelGrid encompasses:\n"
        "1. Real-time catalog searching and distance-based sorting across Puducherry, Chennai, and Bangalore pharmacy corridors.\n"
        "2. Natural language query resolution for colloquial distress terms and snake-bite species using Gemini generative AI.\n"
        "3. Atomic 15-minute hold reservations protected by database optimistic locking.\n"
        "4. Automated background restoration of expired, uncollected reservations.\n"
        "5. Pharmacist-side digital prescription verification and physical dispensation recording.\n"
        "6. Branch manager oversight of staff rosters, working hours, and stock reorder thresholds.\n"
        "7. Network-wide administrative analytics, security audit trails, and system telemetry.\n"
        "8. External pharmacy partnership application intake via embedded Zoho Forms."
    )

    add_subsection_heading("3.2.3 OVERVIEW")
    add_p(
        "SentinelGrid operates as a decoupled client-server web application. The frontend is a Single Page Application (SPA) built with React 19 "
        "and Tailwind CSS, delivering sub-second page transitions and responsive layouts for mobile and desktop screens. The backend is a Spring Boot 3.2 "
        "REST API adhering to clean architecture principles, backed by PostgreSQL 16 for authoritative persistence, with optional Redis caching "
        "and Kafka event streaming for enterprise clustering."
    )

    add_subsection_heading("3.2.4 GENERAL DESCRIPTIONS")
    add_p(
        "The system is designed for high availability, fault tolerance, and data integrity. In the event of cloud provider cold starts or third-party "
        "API outages (e.g., Gemini AI or OpenRouteService), SentinelGrid executes deterministic fallback mechanisms, ensuring core medicine searches "
        "and reservations never fail."
    )

    add_subsection_heading("3.2.4.1 PRODUCT FUNCTION")
    add_p("The platform performs eight core functions:")
    add_p("1. Authenticate users via JWT and enforce role-based route permissions.")
    add_p("2. Normalize free-text medicine queries using generative AI and deterministic regex matching.")
    add_p("3. Compute driving distance, duration, and route geometry between patient GPS coordinates and pharmacy branches.")
    add_p("4. Execute atomic reservations, decrementing available stock and incrementing reserved quantities.")
    add_p("5. Sweep expired reservations every 60 seconds and release held inventory automatically.")
    add_p("6. Enable pharmacists to verify medical prescriptions and confirm pickup with digital signatures.")
    add_p("7. Permit pharmacy managers to add, update, and manage staff schedules and alert rules.")
    add_p("8. Log every security-sensitive transactional event into an immutable audit table.")

    add_subsection_heading("3.2.4.2 USER CHARACTERISTICS")
    add_p("The system accommodates five distinct user profiles:")
    add_p("1. Patient / Bystander: General citizens seeking emergency medicine. Expects immediate results, clear instructions, map directions, and single-click reservation without complex forms.")
    add_p("2. Registered Pharmacist: Clinical staff operating dispensary desks. Needs rapid prescription verification tools, clear reservation lookups, and inventory adjustment capabilities.")
    add_p("3. Pharmacy Branch Manager: Operational supervisor governing branch performance, shift scheduling, low-stock thresholds, and compliance.")
    add_p("4. System Administrator: Healthcare network coordinator monitoring platform uptime, API rate limits, multi-city inventory trends, and system audit logs.")
    add_p("5. Pharmacy Partner Applicant: External pharmacy owner applying to join the network via the embedded Zoho Forms portal.")

    add_subsection_heading("3.2.4.3 GENERAL CONSTRAINTS")
    add_p("• Hardware Budget: Must operate smoothly on ₹0 cloud free-tier hosting (512MB RAM, shared vCPU).")
    add_p("• Database Connections: Neon free-tier limits connections to 5 concurrent active threads; HikariCP must be strictly throttled.")
    add_p("• Reservation Duration: Emergency holds are strictly limited to 15 minutes to prevent drug hoarding.")
    add_p("• Prescription Mandate: Scheduled drugs cannot be dispensed without verified pharmacist sign-off.")
    add_p("• Demo Safety: Public demonstration profile must strictly block destructive deletions of inventory lines and staff records.")

    add_subsection_heading("3.2.5 FUNCTIONAL REQUIREMENTS")
    add_p("• FR-01: User Registration & Authentication (BCrypt 10-round hashing, JWT issuance with role claims).")
    add_p("• FR-02: AI Natural Language Normalization (translates symptoms/vernacular terms into clinical drugs with safety guardrails).")
    add_p("• FR-03: Deterministic Fallback Search (SQL `ILIKE` and trigram matching when AI service is unavailable).")
    add_p("• FR-04: Atomic Reservation Hold (optimistic locking `@Version`, retry logic up to 3 attempts, 15-minute TTL).")
    add_p("• FR-05: Automated Inventory Reversion (Spring `@Scheduled` 60s background daemon releasing expired reservations).")
    add_p("• FR-06: Prescription Verification (image upload, pharmacist audit trail, confirmation code generation).")
    add_p("• FR-07: Branch Staff & Rule Management (role assignment, shift allocation, low-stock notification triggers).")
    add_p("• FR-08: Administrative Observability (real-time KPIs, multi-city stock aggregation, Actuator health endpoints).")
    add_p("• FR-09: Reverse Proxy Rate Limiting (sliding-window IP tracking, 15 req/min on AI endpoints, RFC 7231 headers).")
    add_p("• FR-10: Pharmacy Onboarding (embedded Zoho Forms iframe with fallback links and downloadable mobile QR code).")

    add_subsection_heading("3.2.5.1 TECHNICAL ISSUES")
    add_p(
        "Technical challenges addressed during system development include:\n"
        "1. Concurrency Clashes: When multiple emergency responders attempt to reserve the last ampoule of antivenom simultaneously, standard database read-then-write creates negative stock balances. Resolved using JPA `@Version` optimistic locking.\n"
        "2. IP Address Spoofing Behind CDNs: On cloud edge platforms (Render/Cloudflare), naive extraction of `X-Forwarded-For` allows malicious actors to forge client IPs and bypass rate limits. Resolved through `ClientIpResolver`, validating immediate upstream hops against RFC 1918 private subnets."
    )

    add_subsection_heading("3.2.5.2 RISK ANALYSIS")
    add_p("The following risk matrix governs SentinelGrid's reliability engineering:")
    add_p("• Risk 1: Database Connection Exhaustion -> Mitigation: HikariCP pool sized to max=5, min-idle=1 in `application-demo.yml`.")
    add_p("• Risk 2: Gemini AI Rate Limit / Outage -> Mitigation: Automatic circuit breaker falls back to deterministic SQL search.")
    add_p("• Risk 3: Malicious Deletion of Demo Stock -> Mitigation: `app.demo.protection-enabled=true` strictly blocks `deleteInventory` and `deleteStaff`.")
    add_p("• Risk 4: Patient Prescription Fraud -> Mitigation: Digital audit trail records dispensing pharmacist username and timestamp.")

    add_subsection_heading("3.2.6 INTERFACE REQUIREMENTS")
    add_p(
        "The system provides responsive web interfaces conforming to WCAG 2.1 AA accessibility guidelines. The UI incorporates clear contrast, "
        "spacious touch targets (minimum 44x44px for mobile devices), and immediate visual feedback on all transactional actions."
    )

    add_subsection_heading("3.2.6.1 HARDWARE REQUIREMENTS")
    add_p("Client Hardware (Browser access):")
    add_p("• Processor: Dual-Core 1.5 GHz or higher (Intel Core i3 / AMD Ryzen / ARM Cortex)")
    add_p("• Memory: 2 GB RAM minimum (4 GB recommended)")
    add_p("• Display: 375x667 px mobile screen up to 1920x1080 px desktop monitor")
    add_p("• Network: 3G / 4G / 5G or Broadband Wi-Fi connection (minimum 256 kbps)")
    add_p("Server Hardware (Cloud container):")
    add_p("• Host: Linux x86_64 Container (Render Free Tier)")
    add_p("• Compute: 0.1 vCPU shared, 512 MB RAM")
    add_p("• Storage: 500 MB persistent PostgreSQL storage (Neon Serverless)")

    add_subsection_heading("3.2.6.2 SOFTWARE REQUIREMENTS")
    add_p("Backend Runtime & Libraries:")
    add_p("• JDK: Eclipse Temurin OpenJDK 21 (LTS)")
    add_p("• Framework: Spring Boot 3.2.3 (Web, Data JPA, Security, Actuator, Validation)")
    add_p("• Database: PostgreSQL 16.15 with Flyway 9.22 database migration")
    add_p("• Security: jjwt 0.12.5 (HMAC-SHA256, 256-bit secret entropy)")
    add_p("Frontend Runtime & Libraries:")
    add_p("• Environment: Node.js 20 LTS, Vite 8.2 bundler")
    add_p("• Library: React 19.0, TypeScript 5.2")
    add_p("• Styling: Tailwind CSS 3.4 with custom glassmorphism utilities")
    add_p("• Icons: Lucide React")
    add_p("• Onboarding Integration: Zoho Forms Web Component")

    add_subsection_heading("3.2.7 OTHER FUNCTIONAL ATTRIBUTES")
    add_p("To guarantee enterprise-grade operational reliability, SentinelGrid adheres to four core non-functional pillars:")

    add_subsection_heading("3.2.7.1 SECURITY")
    add_p(
        "Security is woven into every application layer. All communication requires TLS 1.3 encryption. Passwords are salted and hashed "
        "using BCrypt with a cost factor of 10. Authentication tokens utilize HMAC-SHA256 with cryptographically verified 256-bit secret keys. "
        "Role-based access control enforces least-privilege principles across Patient, Pharmacist, Pharmacy Manager, and Admin endpoints. "
        "API endpoints feature IP-based rate limiting to prevent denial-of-service (DoS) attacks and AI token quota depletion."
    )

    add_subsection_heading("3.2.7.2 RELIABILITY")
    add_p(
        "Reliability is ensured through database transaction atomicity and automatic retry mechanisms. In the event of optimistic locking "
        "contention, the system automatically re-reads the latest stock version and retries the reservation up to three times before signaling "
        "depletion. Background daemon tasks running on fixed 60-second intervals guarantee that expired reservation locks are consistently "
        "reconciled, maintaining 99.9% inventory data accuracy."
    )

    add_subsection_heading("3.2.7.3 MAINTAINABILITY")
    add_p(
        "The codebase follows clean architecture principles with complete decoupling between controllers, service interfaces, domain entities, "
        "and data access repositories. Database schema evolutions are managed declaratively using Flyway versioned migration scripts (V1, V2, V3). "
        "Spring profiles (`local`, `demo`, `docker`, `prod`) allow the application to seamlessly run in isolated local development environments "
        "with Redis and Kafka, or in zero-cost cloud configurations without modifying code."
    )

    add_subsection_heading("3.2.7.4 USABILITY")
    add_p(
        "Usability is optimized for high-stress emergency scenarios. The user interface features clear visual hierarchies, high-contrast badges "
        "for stock availability (IN STOCK, LOW STOCK, OUT OF STOCK), 1-click demonstration profile selectors for evaluators, and step-by-step "
        "visual progress trackers for reservation lifecycle stages."
    )

    # =========================================================================
    # CHAPTER 4: SYSTEM ANALYSIS AND DESIGN
    # =========================================================================
    add_chapter_heading("4", "SYSTEM ANALYSIS AND DESIGN")

    add_section_heading("4.1 ARCHITECTURAL DESIGN")
    add_p(
        "SentinelGrid follows a modern 5-tier cloud-native layered architecture designed to achieve high throughput, strict security, and "
        "seamless scalability on serverless cloud infrastructure. The architectural layers comprise:"
    )
    add_p("1. Presentation Tier (Client Layer): React 19 Single Page Application executed in the user's browser, delivering responsive UI views (CareLink, Scriptly, Manager Console, Co-Doctor Hub) with client-side state management (Zustand) and REST API communication via Axios.")
    add_p("2. Reverse Proxy & Edge Tier: Vercel Edge Network routing client traffic, serving compressed static assets, and proxying API calls to the Render cloud backend with TLS termination.")
    add_p("3. Security & Ingress Controller Tier: Spring Security filter chain enforcing JWT authentication, proxy-safe IP resolution (`ClientIpResolver`), sliding-window rate limiting (`RateLimiterService`), and CORS origin validation.")
    add_p("4. Application & Domain Services Tier: Spring Boot 3.2 business services executing domain workflows: `GeminiSearchService` (with fallback logic), `ReservationService` (with optimistic lock retry), `PharmacyService`, `ManagerWorkspaceService`, `RoutingService`, and `AuditLogService`.")
    add_p("5. Persistence & Integration Tier: PostgreSQL 16 relational database with HikariCP connection pooling, Spring Data JPA with Hibernate ORM, Flyway schema migrations, and external REST integrations (Google Gemini API, OpenRouteService, Zoho Forms).")

    add_diagram_box("4.1", "ARCHITECTURAL DESIGN",
                    "Layered Architecture: Presentation (React 19 SPA) → Edge Proxy (Vercel) → Security & Rate Limiting (Spring Security) → Application Services (Spring Boot 3.2) → Persistence (PostgreSQL 16) & External Integrations (Gemini AI, Zoho Forms)")

    add_section_heading("4.2 DATA DESIGN")
    add_p(
        "The relational data design in SentinelGrid is normalized up to Boyce-Codd Normal Form (BCNF) to eliminate data redundancy while "
        "preserving transactional integrity under high-concurrency reservation workloads. The schema is organized into eight primary entities:"
    )
    add_p("• `users`: Stores patient, pharmacist, manager, and administrator identities, BCrypt password hashes, contact details, and optional pharmacy branch bindings.")
    add_p("• `pharmacies`: Represents verified physical pharmacy branches across Puducherry, Chennai, and Bangalore, including street addresses, contact numbers, GPS latitude/longitude, and 24/7 emergency flags.")
    add_p("• `medicines`: Authoritative catalog of emergency medicines, active ingredients, dosage forms, therapeutic categories (Antivenom, Thrombolytic, Cardiac, Antibiotic), and schedule classifications.")
    add_p("• `inventory`: Physical stock records binding a medicine to a pharmacy branch, tracking `quantity`, `reserved_quantity`, low-stock thresholds, and a transactional `version` counter for optimistic locking.")
    add_p("• `reservations`: 15-minute emergency hold transactions tracking `patient_id`, `pharmacy_id`, `medicine_id`, `status` (PENDING, CONFIRMED, EXPIRED, CANCELLED), `pickup_code`, `hold_expires_at`, and `prescription_url`.")
    add_p("• `manager_staff`: Staff member rosters allocated to a pharmacy branch, detailing usernames, shifts (Morning, Evening, Night), roles, and handled reservation counts.")
    add_p("• `manager_settings`: Branch operational rules, automated low-stock alert thresholds, and customized operating hours.")
    add_p("• `audit_logs`: Immutable security audit trails recording entity type, entity ID, action, username, IP address, and timestamp.")

    add_diagram_box("4.2", "DATA DESIGN",
                    "Relational Schema Design: Normalized Tables (users, pharmacies, medicines, inventory, reservations, manager_staff, audit_logs) with Foreign Key Constraints and Optimistic Lock Versioning")

    add_section_heading("4.3 USER INTERFACE DESIGN")
    add_p(
        "SentinelGrid's user interface design adopts a modern, clinical aesthetic emphasizing clarity, immediate readability, and high contrast. "
        "The interface is structured into four distinct, role-dedicated operational portals:"
    )
    add_p("1. CareLink (Patient Portal): Features a prominent hero search bar supporting conversational and vernacular queries (e.g., 'snake bite injection'). Search results display distance-sorted pharmacy cards with real-time stock status (IN STOCK, LOW STOCK), direct Google Maps directions, and an instant 'Reserve Emergency Hold' button.")
    add_p("2. Scriptly (Pharmacist Desk): Provides dispensary staff with real-time incoming reservation alerts, a digital prescription inspection viewer, a one-click 'Verify Prescription' action, and emergency stock adjustment controls.")
    add_p("3. Pharmacy Manager Console: Enables branch supervisors to manage staff rosters, assign shifts, configure low-stock safety thresholds, and monitor branch-level fulfillment ratios.")
    add_p("4. Co-Doctor (Admin Command Center): Equips healthcare administrators with network-wide telemetry, multi-city stock analytics, system health metrics via Spring Boot Actuator, and immutable security audit logs.")
    add_p("5. Pharmacy Partnership Portal: A dedicated public page embedding the official Zoho Forms application, featuring mobile QR-code scanning and complete onboarding compliance instructions.")

    add_diagram_box("4.3", "USER INTERFACE DESIGN",
                    "User Interface Screen Hierarchy: Multi-Role Login Portal → Role Router → Patient CareLink / Pharmacist Scriptly / Manager Console / Admin Co-Doctor / Zoho Pharmacy Partnership Page")

    add_section_heading("4.4 ELEMENTS OF ANALYSIS MODEL")
    add_p(
        "The analysis model establishes the behavioral, structural, and interactional characteristics of the system before software construction. "
        "It encompasses four fundamental elements:"
    )
    add_p("• Scenario-Based Elements: User interaction stories representing how patients discover medicines, pharmacists verify prescriptions, and administrators review system audit trails.")
    add_p("• Class-Based Elements: Object-oriented models capturing entity definitions, domain services, data transfer objects (DTOs), and repository interfaces.")
    add_p("• Behavioral Elements: State transitions dictating how reservations move from PENDING to CONFIRMED, EXPIRED, or CANCELLED based on time and human actions.")
    add_p("• Flow-Oriented Elements: Data flow diagrams (DFD) illustrating how search strings, location coordinates, reservation holds, and verification tokens traverse network boundaries.")

    add_diagram_box("4.4", "ELEMENTS OF ANALYSIS MODEL",
                    "Analysis Model Elements: Scenario-Based (User Stories) + Class-Based (Domain Entities) + Behavioral (State Engine) + Flow-Oriented (Data Flow Architecture)")

    add_subsection_heading("4.4.1 USE CASES AND USE CASE DIAGRAM")
    add_p(
        "The use case model delineates the interactions between primary system actors (Patient, Pharmacist, Pharmacy Manager, Administrator, "
        "and Pharmacy Partner Applicant) and system functionalities. The functional distribution is summarized in Table 4.1:"
    )

    uc_table = doc.add_table(rows=1, cols=3)
    uc_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    uch = uc_table.rows[0].cells
    uch[0].width = Inches(1.2)
    uch[1].width = Inches(2.2)
    uch[2].width = Inches(2.4)
    uch[0].paragraphs[0].add_run("Actor").bold = True
    uch[1].paragraphs[0].add_run("Use Case").bold = True
    uch[2].paragraphs[0].add_run("Description").bold = True

    uc_data = [
        ("Patient", "Search Emergency Medicine", "Search catalog using generic, brand, or colloquial AI symptom queries"),
        ("Patient", "View Route & Distance", "Inspect pharmacy distance, driving duration, and route geometry"),
        ("Patient", "Create Emergency Hold", "Place a guaranteed 15-minute hold on available shelf medicine"),
        ("Patient", "Upload Prescription", "Attach digital prescription copy to pending reservation"),
        ("Pharmacist", "View Branch Queue", "Inspect real-time incoming reservations for the assigned pharmacy"),
        ("Pharmacist", "Verify Prescription", "Review prescription image and digitally sign off on authenticity"),
        ("Pharmacist", "Confirm Pickup / Dispense", "Verify pickup code, decrement physical stock, and finalize sale"),
        ("Pharmacist", "Adjust Inventory Delta", "Manually update stock quantities upon physical delivery receipt"),
        ("Manager", "Manage Staff Roster", "Create, assign shifts, and schedule pharmacy staff pharmacists"),
        ("Manager", "Configure Branch Rules", "Update low-stock thresholds, emergency operating hours, and details"),
        ("Administrator", "Monitor Network Observability", "View multi-city stock aggregation, active reservations, and KPIs"),
        ("Administrator", "Inspect Audit Trail", "Filter and analyze immutable audit logs for all security actions"),
        ("Administrator", "Review System Health", "Monitor JVM memory, Neon database pool, and API latency metrics"),
        ("Partner", "Submit Partnership Application", "Submit drug license and branch details via embedded Zoho Form"),
    ]

    for actor, uc, desc in uc_data:
        row = uc_table.add_row().cells
        row[0].width = Inches(1.2)
        row[1].width = Inches(2.2)
        row[2].width = Inches(2.4)
        row[0].paragraphs[0].add_run(actor)
        row[1].paragraphs[0].add_run(uc)
        row[2].paragraphs[0].add_run(desc)

    add_diagram_box("4.4.1", "USE CASES AND USE CASE DIAGRAM",
                    "Use Case Diagram: Actors (Patient, Pharmacist, Pharmacy Manager, Administrator, Partner) interacting with core platform use cases across authentication, discovery, reservation, verification, and governance")

    add_subsection_heading("4.4.2 SEQUENCE DIAGRAM")
    add_p(
        "The sequence diagram depicts the chronological message exchange between system lifelines during an emergency reservation lifecycle:\n"
        "1. Patient initiates natural language query via the CareLink UI.\n"
        "2. UI dispatches `POST /api/ai/search/normalize` with client IP headers to `AiSearchController`.\n"
        "3. `ClientIpResolver` resolves client IP; `RateLimiterService` validates request quota (<= 15 req/min).\n"
        "4. `GeminiSearchService` normalizes query into canonical medicine name (or executes deterministic fallback).\n"
        "5. `MedicineService` queries `InventoryRepository` for participating pharmacies in the selected city.\n"
        "6. Patient selects a verified pharmacy and clicks 'Reserve Emergency Hold'.\n"
        "7. `ReservationService` attempts atomic inventory hold. It verifies `quantity > reserved_quantity` and updates row with `@Version` increment. In case of concurrent collision, it retries up to 3 times.\n"
        "8. `Reservation` record created with status `PENDING`, unique `pickup_code`, and `hold_expires_at = NOW() + 15 min`.\n"
        "9. Pharmacist receives reservation in Scriptly desk, inspects attached prescription, and clicks 'Verify'.\n"
        "10. Patient arrives at pharmacy; pharmacist inputs pickup code; system transitions status to `CONFIRMED` and decrements both physical quantity and reserved quantity atomically."
    )

    add_diagram_box("4.4.2", "SEQUENCE DIAGRAM",
                    "Sequence Diagram: Chronological interaction between Patient, UI, Security Gateway, AiSearchController, Gemini AI, ReservationService, PostgreSQL Database, and Pharmacist Dispensary Desk")

    add_subsection_heading("4.4.3 ACTIVITY DIAGRAM")
    add_p(
        "The activity diagram models the operational workflow and decision logic governing emergency medicine reservations. "
        "The process begins with patient authentication and medicine discovery. If available inventory exists, the system branches "
        "into the optimistic locking transaction. If a lock conflict occurs, the retry loop executes up to three times. Once locked, "
        "the 15-minute countdown timer starts. If the patient verifies prescription and completes pickup within 15 minutes, the sale "
        "is finalized. If the timer elapses before fulfillment, the background scheduled sweeper detects the expired hold, cancels the "
        "reservation, and restores the held quantity back to available inventory."
    )

    add_diagram_box("4.4.3", "ACTIVITY DIAGRAM",
                    "Activity Diagram: Complete Reservation State Engine Workflow with Decision Diamonds for Stock Availability, Optimistic Lock Retry (1..3), Prescription Verification, and 15-Min TTL Expiration Sweeper")

    add_subsection_heading("4.4.4 CLASS DIAGRAM")
    add_p(
        "The class diagram details the structural relationships, attributes, and methods of the core domain classes within SentinelGrid:\n"
        "• `User`: Attributes (`id`, `username`, `email`, `password`, `role`, `pharmacy`). Methods: `getAuthorities()`, `isEnabled()`.\n"
        "• `Pharmacy`: Attributes (`id`, `name`, `address`, `city`, `latitude`, `longitude`, `contactNumber`, `is24Seven`).\n"
        "• `Medicine`: Attributes (`id`, `name`, `genericName`, `category`, `dosageForm`, `manufacturer`, `prescriptionRequired`).\n"
        "• `Inventory`: Attributes (`id`, `pharmacy`, `medicine`, `quantity`, `reservedQuantity`, `version`). Methods: `getAvailableQuantity()`.\n"
        "• `Reservation`: Attributes (`id`, `patient`, `pharmacy`, `medicine`, `status`, `pickupCode`, `holdExpiresAt`, `prescriptionVerifiedBy`). Methods: `isExpired()`.\n"
        "• `ManagerStaff`: Attributes (`id`, `pharmacy`, `username`, `role`, `shift`, `status`, `reservationsHandled`).\n"
        "• `RateLimiterService`: Methods: `checkAiSearchAllowed(ip)`, `cleanupOldEntries()`.\n"
        "• `ClientIpResolver`: Methods: `resolveClientIp(request)`.\n"
        "• `ReservationService`: Methods: `createReservation()`, `verifyPrescription()`, `confirmPickup()`, `sweepExpiredReservations()`."
    )

    add_diagram_box("4.4.4", "CLASS DIAGRAM",
                    "Class Diagram: Structural model illustrating Domain Entities, JPA Repositories, Business Service Interfaces, REST Controllers, and DTOs with Cardinality Multiplicities")

    add_subsection_heading("4.4.5 E-R DIAGRAM")
    add_p(
        "The Entity-Relationship (E-R) diagram defines the data entities, their attributes, and their relational cardinalities:\n"
        "• `User` (1) to `Reservation` (M): One patient can create multiple reservations over time.\n"
        "• `Pharmacy` (1) to `Inventory` (M): One pharmacy maintains multiple medicine inventory lines.\n"
        "• `Medicine` (1) to `Inventory` (M): One catalog medicine is stocked across multiple pharmacies.\n"
        "• `Pharmacy` (1) to `Reservation` (M): One pharmacy branch fulfills multiple reservations.\n"
        "• `Pharmacy` (1) to `ManagerStaff` (M): One pharmacy branch employs multiple staff members.\n"
        "• `Pharmacy` (1) to `ManagerSettings` (1): One pharmacy branch maintains exactly one configuration record."
    )

    add_diagram_box("4.4.5", "E-R DIAGRAM",
                    "Entity-Relationship Diagram: Complete ER model showing entities, primary/foreign keys, attributes, and 1:1, 1:N, and M:N relationships across SentinelGrid database tables")

    # =========================================================================
    # CHAPTER 5: IMPLEMENTATION
    # =========================================================================
    add_chapter_heading("5", "IMPLEMENTATION")
    add_p(
        "The implementation of SentinelGrid transforms the architectural design and analysis specifications into a production-grade, "
        "highly performant healthcare software application. The implementation adheres strictly to enterprise Java and modern TypeScript "
        "development standards, emphasizing modularity, thread safety, ACID transaction compliance, and defense-in-depth security."
    )
    add_p(
        "The backend is developed using Spring Boot 3.2.3 running on Java 21 LTS. Database persistence is managed via Spring Data JPA with "
        "Hibernate ORM, utilizing PostgreSQL 16.15. The database migration foundation is established through versioned Flyway scripts (`V1__init.sql`, "
        "`V2__synthetic_demo_pharmacies.sql`, `V3__demo_baseline_data.sql`), guaranteeing deterministic schema builds across local and cloud environments."
    )

    add_section_heading("5.1 Module Implementation Overview")
    add_p("The system implementation comprises nine cohesive modules:")

    add_subsection_heading("1. Authentication and Access Control Module (`com.sentragrid.auth`)")
    add_p(
        "Implements stateless authentication utilizing JSON Web Tokens (JWT) signed via HMAC-SHA256 (`jjwt 0.12.5`). Passwords are encrypted "
        "using Spring Security's `BCryptPasswordEncoder` with a strength factor of 10. The `JwtAuthenticationFilter` intercepts every incoming HTTP "
        "request, parses the `Authorization: Bearer <token>` header, validates token signature and expiration against the application's 256-bit "
        "secret, and populates the `SecurityContextHolder` with authenticated principal details and role authorities (`ROLE_PATIENT`, `ROLE_PHARMACIST`, "
        "`ROLE_PHARMACY_MANAGER`, `ROLE_ADMIN`). User registration is strictly confined to the `PATIENT` role to prevent unauthorized privilege escalation."
    )

    add_subsection_heading("2. AI Search Normalization Engine (`com.sentragrid.ai`)")
    add_p(
        "Encapsulated in `GeminiSearchService` and `AiSearchController`. The module integrates with Google Gemini 1.5 Flash via REST endpoints. "
        "When a user enters natural language distress queries (e.g., 'krait bite injection' or 'snake bite medicine'), the engine evaluates the "
        "text against strict clinical safety prompt templates. If the query reflects symptoms requiring emergency hospitalization (e.g., 'severe chest pain'), "
        "the engine triggers a clinical safety guardrail, advising the patient to dial 108/112 immediately. If the query refers to an emergency drug, "
        "it normalizes the text into canonical pharmaceutical titles (e.g., 'Polyvalent Anti-Snake Venom Serum'). In the event of API timeout or "
        "quota depletion, an automated circuit breaker falls back to deterministic SQL trigram and `ILIKE` pattern matching."
    )

    add_subsection_heading("3. Concurrency-Safe Inventory Management (`com.sentragrid.service.PharmacyService`)")
    add_p(
        "Governs real-time stock levels across verified pharmacy branches. Each `Inventory` entity is equipped with a `@Version Long version` "
        "field. When stock adjustments, receipts, or reservations occur, Hibernate verifies the database row version against the in-memory entity version. "
        "If a concurrent transaction modified the row in the interim, an `ObjectOptimisticLockingFailureException` is raised, preventing lost updates "
        "and negative inventory balances. The service also enforces branch scoping, ensuring pharmacists can only modify stock belonging to their "
        "assigned pharmacy."
    )

    add_subsection_heading("4. 15-Minute Reservation State Engine (`com.sentragrid.service.ReservationService`)")
    add_p(
        "Manages the complete lifecycle of emergency holds. When a patient initiates a hold, `ReservationService` attempts an atomic update on "
        "the selected inventory line. If an optimistic lock conflict occurs, the service executes an automatic exponential backoff retry loop "
        "(up to 3 attempts). Upon successful reservation, a unique 6-character alphanumeric pickup code is generated, and `hold_expires_at` is set "
        "to exactly 15 minutes in the future. A background daemon method `@Scheduled(fixedRate = 60000)` sweeps the database every minute, identifying "
        "all reservations with `status = 'PENDING'` and `hold_expires_at < NOW()`, atomically cancelling them and returning the reserved units back "
        "to available shelf stock."
    )

    add_subsection_heading("5. Pharmacist Prescription Verification & Dispensing Desk")
    add_p(
        "Implemented within the Scriptly frontend and `ReservationService`. For Schedule H/X emergency drugs requiring medical authorization, "
        "the patient attaches a digital prescription image. The dispensary pharmacist reviews the prescription image, verifies the prescribing doctor's "
        "registration number, and executes the verification endpoint (`/api/reservations/{id}/verify-prescription`). The verifying pharmacist's username "
        "is immutably recorded. Upon physical arrival, the patient presents the pickup code, and the pharmacist confirms dispensation (`/api/reservations/{id}/confirm-pickup`), "
        "permanently decrementing physical inventory."
    )

    add_subsection_heading("6. Pharmacy Branch Management Console (`com.sentragrid.manager`)")
    add_p(
        "Provides pharmacy managers with operational governance over their branch. Implemented in `ManagerWorkspaceService` and `PharmacyManagerPage.tsx`. "
        "Managers can configure low-stock warning thresholds (e.g., alert when antivenom drops below 5 units), establish operating hours (including 24/7 "
        "casualty support flags), and manage staff rosters (`ManagerStaff`) by scheduling morning, evening, and night shifts."
    )

    add_subsection_heading("7. Administrative Command Center & Telemetry (`com.sentragrid.dashboard`)")
    add_p(
        "The Co-Doctor enterprise portal equips health system administrators with real-time network oversight. `DashboardService` aggregates "
        "stock availability, active hold counts, and reservation fulfillment rates across Puducherry, Chennai, and Bangalore. The module also "
        "integrates Spring Boot Actuator (`/actuator/health`, `/actuator/metrics`) to monitor JVM memory, HikariCP database pool utilization, "
        "and garbage collection telemetry."
    )

    add_subsection_heading("8. Reverse Proxy IP Resolution & Anti-Abuse Rate Limiter (`com.sentragrid.security`)")
    add_p(
        "Engineered in `ClientIpResolver` and `RateLimiterService`. In cloud deployments behind reverse proxies (Render ingress, Vercel edge), "
        "the immediate socket remote address is an internal proxy IP. `ClientIpResolver` parses the `X-Forwarded-For` header from right to left, "
        "verifying that untrusted public IPs are identified accurately without trusting spoofed client headers or `0.0.0.0/0`. `RateLimiterService` "
        "maintains an in-memory sliding-window token bucket tracking per-client IP search queries, strictly enforcing a maximum of 15 queries per minute "
        "on AI endpoints and returning HTTP 429 with RFC 7231 `Retry-After: 60` headers."
    )

    add_subsection_heading("9. Zoho Forms Pharmacy Partnership Application Integration")
    add_p(
        "Provides a frictionless onboarding gateway for external licensed pharmacies. Implemented in `PharmacyPartnershipPage.tsx`. The page embeds "
        "the verified Zoho Forms application via a responsive, accessible iframe, accompanied by strict compliance notices emphasizing that submission "
        "does not constitute automatic approval. The page also features a downloadable mobile QR code (`zoho-pharmacy-qr.png`), allowing field evaluators "
        "and pharmacy owners to open the application directly on mobile during site visits."
    )

    # =========================================================================
    # CHAPTER 6: TESTING
    # =========================================================================
    add_chapter_heading("6", "TESTING")

    add_section_heading("6.1 TEST PLAN")
    add_p(
        "Quality assurance is integral to SentinelGrid's reliability. Because the platform manages life-saving medications where concurrency "
        "failures or data desynchronization could lead to fatal stockouts, a multi-phase testing strategy was formulated and executed. The test plan "
        "encompasses Unit Testing (JUnit 5, Mockito), Integration Testing (Spring Boot Test, TestRestTemplate, Flyway), Concurrency Stress Testing "
        "(multi-threaded thread pool contention), Validation Testing (clinical guardrails, schema bounds), and Frontend End-to-End Build Verification."
    )

    add_section_heading("6.2 UNIT TESTING")
    add_p(
        "Unit testing isolates individual business logic classes and validates behavior using Mockito mocks. All repository queries, service "
        "methods, and security filters are covered. The comprehensive backend test suite contains 239 automated test cases, achieving a 100% "
        "pass rate (239 tests run, 0 failures, 0 errors, 0 skipped). A representative sample of unit test suites is detailed in Table 6.1:"
    )

    t_table = doc.add_table(rows=1, cols=4)
    t_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    th = t_table.rows[0].cells
    th[0].width = Inches(1.8)
    th[1].width = Inches(1.8)
    th[2].width = Inches(1.2)
    th[3].width = Inches(1.0)
    th[0].paragraphs[0].add_run("Test Suite Class").bold = True
    th[1].paragraphs[0].add_run("Focus Area").bold = True
    th[2].paragraphs[0].add_run("Test Count").bold = True
    th[3].paragraphs[0].add_run("Status").bold = True

    unit_test_data = [
        ("ReservationServiceTest", "Optimistic locking, retry logic, TTL expiry", "28 Tests", "PASSED"),
        ("ReservationSingleAttemptTest", "Single attempt transaction boundaries", "8 Tests", "PASSED"),
        ("AiSearchRateLimitingTest", "15 req/min sliding window, HTTP 429", "5 Tests", "PASSED"),
        ("DemoProtectionHardeningTest", "Block destructive deletions in demo", "4 Tests", "PASSED"),
        ("CloudDemoProfileSafetyTest", "Neon Hikari pool, Kafka/Redis exclusion", "15 Tests", "PASSED"),
        ("ProfileIsolationAndStartupTest", "Spring profile validation & security", "18 Tests", "PASSED"),
        ("MedicineSearchSafetyTest", "Clinical guardrail symptom triage", "12 Tests", "PASSED"),
        ("PharmacyIsolationTest", "Branch scoping & stock integrity", "14 Tests", "PASSED"),
        ("RoutingServiceTest", "GIS distance calculation & ORS fallback", "3 Tests", "PASSED"),
    ]

    for tc, focus, cnt, stat in unit_test_data:
        row = t_table.add_row().cells
        row[0].width = Inches(1.8)
        row[1].width = Inches(1.8)
        row[2].width = Inches(1.2)
        row[3].width = Inches(1.0)
        row[0].paragraphs[0].add_run(tc)
        row[1].paragraphs[0].add_run(focus)
        row[2].paragraphs[0].add_run(cnt)
        row[3].paragraphs[0].add_run(stat)

    add_section_heading("6.3 INTEGRATION TESTING")
    add_p(
        "Integration testing verifies cross-module interaction against a real PostgreSQL database instance. Key integration suites include:\n"
        "1. Concurrency Stress Testing (`ReservationConcurrencyTest`): 20 concurrent threads simulated simultaneous reservation attempts against a single remaining unit of snake antivenom. Exactly one thread successfully locked the inventory row, while the remaining 19 threads cleanly caught optimistic lock exceptions and received friendly 'Stock Exhausted' responses. Zero negative balances occurred.\n"
        "2. Flyway Migration Integrity (`DatabaseSeedAndIntegrityTest`): Validates that migrations V1, V2, and V3 apply deterministically, seed synthetic pharmacy locations across three cities, and establish foreign key constraints without error.\n"
        "3. HTTP Security Filter Chain (`PharmacySecurityHttpIntegrationTest`): Verifies that unauthenticated requests to protected endpoints return HTTP 401 Unauthorized, and unauthorized role attempts return HTTP 403 Forbidden."
    )

    add_section_heading("6.4 VALIDATION TESTING")
    add_p(
        "Validation testing confirms that user inputs and clinical edge cases conform to expected safety rules:\n"
        "• Snake-Bite Natural Language Resolution: Tested colloquial phrases including 'krait bite', 'viper bite injection', and 'snake poison remedy'. In each case, the system resolved the query to 'Polyvalent Anti-Snake Venom Serum' and returned geographically nearest stock.\n"
        "• Medical Malpractice Guardrail: Tested queries describing acute unconfirmed symptoms (e.g., 'severe left arm pain and sweating'). The AI engine strictly refused self-diagnostic prescribing and displayed emergency notice: 'Dial 108/112 immediately'.\n"
        "• Public Demo Protection: In demo mode (`app.demo.protection-enabled=true`), API calls attempting `DELETE /api/pharmacies/{id}/inventory/{invId}` and `DELETE /api/manager/staff/{id}` were intercepted and rejected with HTTP 400 Bad Request, preserving baseline demo data."
    )

    # =========================================================================
    # CHAPTER 7: CONCLUSION & FUTURE ENHANCEMENTS
    # =========================================================================
    add_chapter_heading("7", "CONCLUSION & FUTURE ENHANCEMENTS")

    add_section_heading("7.1 CONCLUSION")
    add_p(
        "SentinelGrid demonstrates that modern distributed cloud computing, generative AI, and robust database concurrency control can "
        "be harmoniously synthesized to solve one of the most critical challenges in emergency healthcare: the rapid discovery and guaranteed "
        "reservation of life-saving medicines during the Golden Hour."
    )
    add_p(
        "By replacing slow, chaotic manual telephone calls with a sub-second, multi-tier digital network, SentinelGrid reduces emergency medicine "
        "discovery latency from hours to seconds. The platform's Gemini AI engine bridges the communication gap between frantic citizens and formal "
        "pharmacological catalogs without creating clinical liability risks. The 15-minute optimistic-locking reservation engine guarantees that a patient "
        "traveling to a pharmacy will find their medicine waiting, while automated background sweepers ensure uncollected medication is never permanently "
        "locked away. Delivered on a ₹0 public cloud architecture utilizing Render, Neon, Vercel, and Zoho Forms, SentinelGrid provides an accessible, "
        "sustainable blueprint for modern emergency pharmaceutical logistics."
    )

    add_section_heading("7.2 FUTURE ENHANCEMENTS")
    add_p("The platform's extensible architecture provides a strong foundation for future enterprise enhancements:")
    add_p("1. Zoho Desk Customer Support Integration: Embedding an advanced ticketing portal on the Help & Support page to handle patient reservation disputes, pharmacist queries, and service feedback.")
    add_p("2. Autonomous Drone Delivery Dispatch: Interfacing with medical drone logistics APIs to automatically dispatch emergency antivenom payloads directly to remote primary health centres (PHCs) when road transit exceeds 30 minutes.")
    add_p("3. Ayushman Bharat Digital Mission (ABDM) Integration: Connecting with the national Ayushman Bharat Health Account (ABHA) ecosystem to enable instant electronic prescription pull from hospital EMRs.")
    add_p("4. IoT Cold-Chain Sensor Telemetry: Integrating smart refrigerator temperature sensors (BLE/MQTT) into the inventory record to verify that temperature-sensitive biologics (antivenoms, insulins) have maintained cold-chain compliance before dispensation.")

    # =========================================================================
    # BIBLIOGRAPHY
    # =========================================================================
    doc.add_page_break()
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(12)
    p.paragraph_format.space_after = Pt(20)
    r = p.add_run("BIBLIOGRAPHY")
    r.bold = True
    r.font.size = Pt(13)

    bib_entries = [
        "1. Pressman, R. S. and Maxim, B. R., Software Engineering: A Practitioner's Approach, 9th Edition, McGraw-Hill Education, 2020.",
        "2. Silberschatz, A., Korth, H. F. and Sudarshan, S., Database System Concepts, 7th Edition, McGraw-Hill Education, 2019.",
        "3. Elmasri, R. and Navathe, S. B., Fundamentals of Database Systems, 7th Edition, Pearson Education, 2017.",
        "4. Fowler, M., Patterns of Enterprise Application Architecture, Addison-Wesley Professional, 2002.",
        "5. Walls, C., Spring in Action, 6th Edition, Manning Publications, 2022.",
        "6. Spring Boot 3.2 Reference Documentation. Available at: https://docs.spring.io/spring-boot/docs/3.2.x/reference/html/",
        "7. React Official Documentation. Available at: https://react.dev/",
        "8. PostgreSQL 16 Official Documentation. Available at: https://www.postgresql.org/docs/16/",
        "9. Google Gemini AI API Documentation. Available at: https://ai.google.dev/docs",
        "10. OWASP Top Ten Web Application Security Risks. Available at: https://owasp.org/www-project-top-ten/",
        "11. OpenRouteService API Documentation. Available at: https://openrouteservice.org/dev/#/api-docs",
        "12. World Health Organization (WHO), Guidelines for the Management of Snakebites, 2nd Edition, 2016."
    ]

    for b in bib_entries:
        add_p(b, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=0, space_after=8)

    # =========================================================================
    # APPENDIX A: TABLES (DATA DICTIONARY)
    # =========================================================================
    doc.add_page_break()
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(12)
    p.paragraph_format.space_after = Pt(4)
    r = p.add_run("APPENDIX A")
    r.bold = True
    r.font.size = Pt(14)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(18)
    r = p.add_run("TABLES (DATA DICTIONARY)")
    r.bold = True
    r.font.size = Pt(13)

    def add_data_table(title_str, desc_str, headers, rows_data):
        tp = doc.add_paragraph()
        tp.paragraph_format.space_before = Pt(12)
        tp.paragraph_format.space_after = Pt(2)
        r = tp.add_run(title_str)
        r.bold = True
        r.font.size = Pt(11)

        dp = doc.add_paragraph()
        dp.paragraph_format.space_after = Pt(6)
        r = dp.add_run(f"Descriptions : {desc_str}")
        r.italic = True
        r.font.size = Pt(10)

        tbl = doc.add_table(rows=1, cols=len(headers))
        tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
        hdr_cells = tbl.rows[0].cells
        for i, h in enumerate(headers):
            hdr_cells[i].paragraphs[0].add_run(h).bold = True
            hdr_cells[i].paragraphs[0].runs[0].font.name = 'Times New Roman'
            hdr_cells[i].paragraphs[0].runs[0].font.size = Pt(10)
            set_cell_shading(hdr_cells[i], 'E2E8F0')

        for r_data in rows_data:
            row_cells = tbl.add_row().cells
            for i, val in enumerate(r_data):
                p = row_cells[i].paragraphs[0]
                run = p.add_run(str(val))
                run.font.name = 'Times New Roman'
                run.font.size = Pt(9.5)
                p.paragraph_format.space_after = Pt(2)
                p.paragraph_format.space_before = Pt(2)

    add_data_table(
        "User Table: A.1",
        "Stores registered user accounts, encrypted credentials, contact details, and role assignments for RBAC.",
        ["S.No", "Field Name", "Data Type", "Size / Precision", "Constraints"],
        [
            ("1", "id", "BIGINT", "64-bit", "PRIMARY KEY, AUTO_INCREMENT"),
            ("2", "username", "VARCHAR", "50", "NOT NULL, UNIQUE"),
            ("3", "email", "VARCHAR", "100", "NOT NULL, UNIQUE"),
            ("4", "password", "VARCHAR", "255", "NOT NULL (BCrypt Hash)"),
            ("5", "role", "VARCHAR", "30", "NOT NULL (PATIENT, PHARMACIST, etc.)"),
            ("6", "pharmacy_id", "BIGINT", "64-bit", "FOREIGN KEY -> pharmacies(id), NULLABLE"),
            ("7", "created_at", "TIMESTAMP", "Default CURRENT_TIMESTAMP", "NOT NULL"),
            ("8", "updated_at", "TIMESTAMP", "Default CURRENT_TIMESTAMP", "NOT NULL"),
        ]
    )

    add_data_table(
        "Pharmacy Table: A.2",
        "Maintains physical pharmacy branch records, addresses, contact details, GPS coordinates, and operating hours.",
        ["S.No", "Field Name", "Data Type", "Size / Precision", "Constraints"],
        [
            ("1", "id", "BIGINT", "64-bit", "PRIMARY KEY, AUTO_INCREMENT"),
            ("2", "name", "VARCHAR", "150", "NOT NULL"),
            ("3", "address", "VARCHAR", "255", "NOT NULL"),
            ("4", "city", "VARCHAR", "80", "NOT NULL (Puducherry, Chennai, etc.)"),
            ("5", "latitude", "DOUBLE PRECISION", "Floating Point", "NOT NULL"),
            ("6", "longitude", "DOUBLE PRECISION", "Floating Point", "NOT NULL"),
            ("7", "contact_number", "VARCHAR", "30", "NOT NULL"),
            ("8", "is_24_seven", "BOOLEAN", "1-bit", "DEFAULT FALSE, NOT NULL"),
            ("9", "opening_hours", "VARCHAR", "80", "NULLABLE"),
        ]
    )

    add_data_table(
        "Medicine Table: A.3",
        "Authoritative catalog of certified emergency pharmaceuticals, formulations, active compounds, and schedules.",
        ["S.No", "Field Name", "Data Type", "Size / Precision", "Constraints"],
        [
            ("1", "id", "BIGINT", "64-bit", "PRIMARY KEY, AUTO_INCREMENT"),
            ("2", "name", "VARCHAR", "150", "NOT NULL, UNIQUE"),
            ("3", "generic_name", "VARCHAR", "150", "NOT NULL"),
            ("4", "category", "VARCHAR", "80", "NOT NULL (Antivenom, Cardiac, etc.)"),
            ("5", "dosage_form", "VARCHAR", "50", "NOT NULL (Injection, Tablet, Vial)"),
            ("6", "manufacturer", "VARCHAR", "120", "NOT NULL"),
            ("7", "prescription_required", "BOOLEAN", "1-bit", "DEFAULT TRUE, NOT NULL"),
            ("8", "description", "TEXT", "Variable", "NULLABLE"),
        ]
    )

    add_data_table(
        "Inventory Table: A.4",
        "Tracks real-time stock levels per pharmacy branch with optimistic locking version counters.",
        ["S.No", "Field Name", "Data Type", "Size / Precision", "Constraints"],
        [
            ("1", "id", "BIGINT", "64-bit", "PRIMARY KEY, AUTO_INCREMENT"),
            ("2", "pharmacy_id", "BIGINT", "64-bit", "FOREIGN KEY -> pharmacies(id), NOT NULL"),
            ("3", "medicine_id", "BIGINT", "64-bit", "FOREIGN KEY -> medicines(id), NOT NULL"),
            ("4", "quantity", "INTEGER", "32-bit", "NOT NULL, CHECK (quantity >= 0)"),
            ("5", "reserved_quantity", "INTEGER", "32-bit", "NOT NULL, CHECK (reserved_quantity >= 0)"),
            ("6", "price", "DECIMAL", "10, 2", "NOT NULL"),
            ("7", "version", "BIGINT", "64-bit", "NOT NULL (JPA @Version Lock)"),
            ("8", "updated_at", "TIMESTAMP", "Timestamp", "NOT NULL"),
        ]
    )

    add_data_table(
        "Reservation Table: A.5",
        "Maintains time-limited 15-minute emergency hold records, pickup codes, and verification lifecycle states.",
        ["S.No", "Field Name", "Data Type", "Size / Precision", "Constraints"],
        [
            ("1", "id", "BIGINT", "64-bit", "PRIMARY KEY, AUTO_INCREMENT"),
            ("2", "patient_id", "BIGINT", "64-bit", "FOREIGN KEY -> users(id), NOT NULL"),
            ("3", "pharmacy_id", "BIGINT", "64-bit", "FOREIGN KEY -> pharmacies(id), NOT NULL"),
            ("4", "medicine_id", "BIGINT", "64-bit", "FOREIGN KEY -> medicines(id), NOT NULL"),
            ("5", "status", "VARCHAR", "30", "NOT NULL (PENDING, CONFIRMED, etc.)"),
            ("6", "pickup_code", "VARCHAR", "20", "NOT NULL, UNIQUE"),
            ("7", "hold_expires_at", "TIMESTAMP", "Timestamp", "NOT NULL (NOW() + 15 min)"),
            ("8", "prescription_url", "VARCHAR", "255", "NULLABLE"),
            ("9", "prescription_verified_by", "VARCHAR", "50", "NULLABLE"),
        ]
    )

    add_data_table(
        "Manager Staff Table: A.6",
        "Records pharmacy branch staff assignments, working shifts, and handled reservation counts.",
        ["S.No", "Field Name", "Data Type", "Size / Precision", "Constraints"],
        [
            ("1", "id", "BIGINT", "64-bit", "PRIMARY KEY, AUTO_INCREMENT"),
            ("2", "pharmacy_id", "BIGINT", "64-bit", "FOREIGN KEY -> pharmacies(id), NOT NULL"),
            ("3", "username", "VARCHAR", "50", "NOT NULL, UNIQUE"),
            ("4", "role", "VARCHAR", "30", "NOT NULL"),
            ("5", "shift", "VARCHAR", "30", "NOT NULL (Morning, Evening, Night)"),
            ("6", "status", "VARCHAR", "20", "NOT NULL (Active, On-Leave)"),
            ("7", "reservations_handled", "INTEGER", "32-bit", "DEFAULT 0, NOT NULL"),
        ]
    )

    add_data_table(
        "Audit Log Table: A.7",
        "Maintains immutable audit records for all security-sensitive actions and inventory adjustments.",
        ["S.No", "Field Name", "Data Type", "Size / Precision", "Constraints"],
        [
            ("1", "id", "BIGINT", "64-bit", "PRIMARY KEY, AUTO_INCREMENT"),
            ("2", "entity_type", "VARCHAR", "50", "NOT NULL (Reservation, Inventory, etc.)"),
            ("3", "entity_id", "BIGINT", "64-bit", "NOT NULL"),
            ("4", "action", "VARCHAR", "50", "NOT NULL (CONFIRMED, ADJUSTED, etc.)"),
            ("5", "performed_by", "VARCHAR", "50", "NOT NULL (Username)"),
            ("6", "details", "TEXT", "Variable", "NULLABLE"),
            ("7", "timestamp", "TIMESTAMP", "Default CURRENT_TIMESTAMP", "NOT NULL"),
        ]
    )

    # =========================================================================
    # APPENDIX B: SCREENSHOTS
    # =========================================================================
    doc.add_page_break()
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(12)
    p.paragraph_format.space_after = Pt(4)
    r = p.add_run("APPENDIX B")
    r.bold = True
    r.font.size = Pt(14)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(18)
    r = p.add_run("SCREENSHOTS")
    r.bold = True
    r.font.size = Pt(13)

    add_screenshot_box("B[1]", "Desktop Login & Multi-Role Demo Switcher",
                       "Expansive two-column authentication portal showing brand showcase, 4 workspace overview cards, and 1-click presets for Patient, Pharmacist, Manager, and Admin.")
    add_screenshot_box("B[2]", "Patient Emergency Medicine Search (CareLink)",
                       "Search results display showing real-time antivenom stock availability, distance sorting, verified pharmacy details, and Google Maps directions.")
    add_screenshot_box("B[3]", "Snake-Bite Colloquial NLP Query Resolution",
                       "Demonstration of natural language query 'krait bite injection' mapped automatically by Gemini AI to 'Polyvalent Anti-Snake Venom Serum'.")
    add_screenshot_box("B[4]", "Clinical Emergency Safety Guardrail Notice",
                       "System triage banner advising immediate ambulance dispatch (Dial 108/112) when an acute clinical symptom is entered.")
    add_screenshot_box("B[5]", "Pharmacy Network & Route Map Navigation",
                       "Interactive corridor map displaying participating pharmacy hubs across Puducherry, Chennai, and Bangalore with direct directions.")
    add_screenshot_box("B[6]", "15-Minute Guaranteed Hold Reservation Screen",
                       "Confirmed reservation card displaying active 15-minute countdown timer, unique pickup code, and digital prescription upload facility.")
    add_screenshot_box("B[7]", "Scriptly Pharmacist Desk & Prescription Verification",
                       "Dispensary management queue where pharmacists inspect patient prescription images and digitally verify medical authorization.")
    add_screenshot_box("B[8]", "Pharmacist Inventory Management & Stock Adjustment",
                       "Inventory management table allowing authorized pharmacists to update stock levels, log stock deliveries, and review reserved units.")
    add_screenshot_box("B[9]", "Pharmacy Manager Workspace & Staff Scheduling",
                       "Administrative console showing staff shift rosters, employee statuses, and low-stock safety threshold governance rules.")
    add_screenshot_box("B[10]", "Co-Doctor Administrative Command Center",
                       "Executive dashboard aggregating network-wide emergency stock levels, city-wise reservation volumes, and hospital fulfillment KPIs.")
    add_screenshot_box("B[11]", "System Health Telemetry & Security Audit Trail",
                       "Real-time Spring Boot Actuator monitoring showing JVM memory, Neon database pool health, and immutable audit logs.")
    add_screenshot_box("B[12]", "Zoho Forms Pharmacy Partnership Application",
                       "Dedicated public onboarding page embedding the verified Zoho Forms partnership portal with mobile QR-code scanning support.")

    # =========================================================================
    # APPENDIX C: SAMPLE CODING
    # =========================================================================
    doc.add_page_break()
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(12)
    p.paragraph_format.space_after = Pt(4)
    r = p.add_run("APPENDIX C")
    r.bold = True
    r.font.size = Pt(14)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(18)
    r = p.add_run("SAMPLE CODING")
    r.bold = True
    r.font.size = Pt(13)

    def add_code_block(title_str, code_str):
        tp = doc.add_paragraph()
        tp.paragraph_format.space_before = Pt(12)
        tp.paragraph_format.space_after = Pt(4)
        r = tp.add_run(f"// --- {title_str} ---")
        r.bold = True
        r.font.name = 'Courier New'
        r.font.size = Pt(10)
        r.font.color.rgb = RGBColor(0, 51, 102)

        tbl = doc.add_table(rows=1, cols=1)
        tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
        cell = tbl.cell(0, 0)
        cell.width = Inches(5.8)
        set_cell_border(cell,
                        top=dict(val='single', sz='4', color='CCCCCC'),
                        bottom=dict(val='single', sz='4', color='CCCCCC'),
                        left=dict(val='single', sz='4', color='CCCCCC'),
                        right=dict(val='single', sz='4', color='CCCCCC'))
        set_cell_shading(cell, 'F8FAFC')

        cp = cell.paragraphs[0]
        cp.paragraph_format.space_before = Pt(6)
        cp.paragraph_format.space_after = Pt(6)
        cp.paragraph_format.line_spacing = 1.15
        cr = cp.add_run(code_str.strip())
        cr.font.name = 'Courier New'
        cr.font.size = Pt(8.5)
        cr.font.color.rgb = RGBColor(30, 41, 59)

    add_code_block("ReservationService.java (Optimistic Locking & Hold Engine)", """
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public ReservationResponseDto createReservation(ReservationRequestDto request, String patientUsername) {
        User patient = userRepository.findByUsername(patientUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Patient account not found: " + patientUsername));

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Inventory inventory = inventoryRepository.findById(request.getInventoryId())
                        .orElseThrow(() -> new ResourceNotFoundException("Inventory not found: " + request.getInventoryId()));

                int availableStock = inventory.getQuantity() - inventory.getReservedQuantity();
                if (availableStock <= 0) {
                    throw new BadRequestException("Requested medicine is currently out of available stock.");
                }

                // Atomic hold increment protected by JPA @Version
                inventory.setReservedQuantity(inventory.getReservedQuantity() + 1);
                inventoryRepository.saveAndFlush(inventory);

                Reservation reservation = Reservation.builder()
                        .patient(patient)
                        .pharmacy(inventory.getPharmacy())
                        .medicine(inventory.getMedicine())
                        .status(ReservationStatus.PENDING)
                        .pickupCode(generatePickupCode())
                        .holdExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                        .prescriptionUrl(request.getPrescriptionUrl())
                        .build();

                Reservation saved = reservationRepository.save(reservation);
                auditLogService.logAction("Reservation", saved.getId(), "RESERVATION_CREATED",
                        patientUsername, "15-minute emergency hold secured");
                return mapToDto(saved);

            } catch (ObjectOptimisticLockingFailureException ex) {
                log.warn("Optimistic lock conflict on attempt {}/{} for patient {}", attempt, maxRetries, patientUsername);
                if (attempt == maxRetries) {
                    throw new ConcurrencyException("High demand detected. Stock reservation collided; please retry.");
                }
            }
        }
        throw new ConcurrencyException("Unable to secure stock reservation after multiple attempts.");
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void sweepExpiredReservations() {
        List<Reservation> expiredList = reservationRepository
                .findByStatusAndHoldExpiresAtBefore(ReservationStatus.PENDING, Instant.now());

        for (Reservation res : expiredList) {
            res.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(res);

            // Revert reserved stock atomically
            inventoryRepository.findByPharmacyIdAndMedicineId(res.getPharmacy().getId(), res.getMedicine().getId())
                    .ifPresent(inv -> {
                        inv.setReservedQuantity(Math.max(0, inv.getReservedQuantity() - 1));
                        inventoryRepository.save(inv);
                    });
            log.info("Expired reservation ID {} swept and stock restored.", res.getId());
        }
    }
}
""")

    add_code_block("RateLimiterService.java (Proxy-Aware 15 req/min Sliding Window)", """
@Service
@Slf4j
public class RateLimiterService {

    private static final int MAX_REQUESTS_PER_MINUTE = 15;
    private static final long WINDOW_MILLIS = 60_000L;

    private final ConcurrentHashMap<String, List<Long>> requestCounts = new ConcurrentHashMap<>();

    public void checkAiSearchAllowed(String clientIp) {
        String key = (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "UNKNOWN";
        long now = System.currentTimeMillis();

        List<Long> timestamps = requestCounts.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));

        synchronized (timestamps) {
            // Sliding window eviction of expired timestamps
            timestamps.removeIf(t -> (now - t) > WINDOW_MILLIS);

            if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
                log.warn("AI search rate limit breached for IP [{}]. Count: {}. Retry-After: 60s", key, timestamps.size());
                throw new RateLimitExceededException("AI Search rate limit exceeded (15 requests/minute). Please wait.", 60);
            }
            timestamps.add(now);
        }
    }
}
""")

    add_code_block("ClientIpResolver.java (RFC 1918 Reverse-Proxy Header Resolution)", """
@Component
public class ClientIpResolver {

    @Value("${security.trusted-proxies:127.0.0.1,::1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}")
    private String trustedProxiesConfig;

    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String xForwardedFor = request.getHeader("X-Forwarded-For");

        if (xForwardedFor == null || xForwardedFor.isBlank()) {
            return remoteAddr != null ? remoteAddr.trim() : "UNKNOWN";
        }

        // Verify immediate upstream connection is a trusted reverse proxy
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr.trim();
        }

        // Parse X-Forwarded-For from right to left to locate the genuine client IP
        String[] parts = xForwardedFor.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String ip = parts[i].trim();
            if (!ip.isEmpty() && !isTrustedProxy(ip)) {
                return ip;
            }
        }
        return remoteAddr.trim();
    }
}
""")

    add_code_block("PharmacyPartnershipPage.tsx (Zoho Forms React Component)", """
export const PharmacyPartnershipPage: React.FC<{ isPublic?: boolean }> = ({ isPublic = false }) => {
  const [iframeLoaded, setIframeLoaded] = useState(false);
  const ZOHO_FORM_URL = "https://forms.zohopublic.in/mappillaimeeran32gm1/form/...";

  return (
    <div className="max-w-6xl mx-auto px-4 py-8 space-y-8">
      <div className="rounded-3xl bg-gradient-to-br from-blue-700 to-indigo-900 p-8 text-white shadow-lg">
        <h1 className="text-3xl font-extrabold">Become a SentinelGrid Pharmacy Partner</h1>
        <p className="text-blue-100 mt-2">Apply to join the emergency medicine availability network.</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        <div className="lg:col-span-8 bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
          <div className="relative min-h-[650px] rounded-xl overflow-hidden border border-slate-100">
            <iframe
              aria-label="SentinelGrid – Pharmacy Partnership Application"
              frameBorder="0"
              style={{ height: '700px', width: '99%', border: 'none' }}
              src={ZOHO_FORM_URL}
              title="SentinelGrid – Pharmacy Partnership Application"
              onLoad={() => setIframeLoaded(true)}
              className="w-full min-h-[650px]"
            />
          </div>
        </div>

        <div className="lg:col-span-4 bg-white p-6 rounded-2xl border border-slate-200 shadow-sm text-center">
          <h3 className="font-bold text-slate-900">Apply via Mobile (QR Code)</h3>
          <img src={zohoQrImage} alt="Zoho Application QR" className="w-44 h-44 mx-auto my-4 rounded-lg" />
          <a href="/zoho-pharmacy-qr.png" download className="text-xs font-semibold text-blue-600">Download QR Image</a>
        </div>
      </div>
    </div>
  );
};
""")

    # =========================================================================
    # APPENDIX D: TECHNOLOGY / SOFTWARE
    # =========================================================================
    doc.add_page_break()
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(12)
    p.paragraph_format.space_after = Pt(4)
    r = p.add_run("APPENDIX D")
    r.bold = True
    r.font.size = Pt(14)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(18)
    r = p.add_run("TECHNOLOGY / SOFTWARE")
    r.bold = True
    r.font.size = Pt(13)

    tech_table = doc.add_table(rows=1, cols=3)
    tech_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    th = tech_table.rows[0].cells
    th[0].width = Inches(0.8)
    th[1].width = Inches(2.2)
    th[2].width = Inches(2.8)
    th[0].paragraphs[0].add_run("S. No.").bold = True
    th[1].paragraphs[0].add_run("Technology / Software").bold = True
    th[2].paragraphs[0].add_run("Purpose").bold = True

    tech_data = [
        ("1", "Java 21 LTS (OpenJDK)", "Core backend programming language with Virtual Threads runtime."),
        ("2", "Spring Boot 3.2.3", "Enterprise web framework providing REST APIs, DI, and auto-configuration."),
        ("3", "Spring Security 6.2", "Role-based access control, JWT verification, and endpoint authorization."),
        ("4", "PostgreSQL 16.15", "ACID-compliant relational database for authoritative persistence."),
        ("5", "Flyway 9.22", "Automated, version-controlled database schema migrations."),
        ("6", "React 19.0", "Declarative frontend Single Page Application (SPA) framework."),
        ("7", "TypeScript 5.2", "Statically typed programming language for robust frontend logic."),
        ("8", "Tailwind CSS 3.4", "Utility-first CSS framework for clean, responsive healthcare styling."),
        ("9", "Vite 8.2", "High-performance frontend build tool and local development server."),
        ("10", "Google Gemini 1.5 Flash", "Generative AI API for natural language symptom and medicine normalization."),
        ("11", "OpenRouteService API", "GIS routing engine computing driving distance and estimated arrival times."),
        ("12", "Zoho Forms", "Secure external web form for pharmacy partnership applications."),
        ("13", "Render Cloud Host", "Linux container hosting the Spring Boot backend on a free-tier footprint."),
        ("14", "Neon Serverless Postgres", "Serverless cloud PostgreSQL 16 database host."),
        ("15", "Vercel Edge Platform", "Global edge network hosting and caching the React frontend."),
    ]

    for s, t, purp in tech_data:
        row = tech_table.add_row().cells
        row[0].width = Inches(0.8)
        row[1].width = Inches(2.2)
        row[2].width = Inches(2.8)
        row[0].paragraphs[0].add_run(s)
        row[1].paragraphs[0].add_run(t).bold = True
        row[2].paragraphs[0].add_run(purp)

    # =========================================================================
    # APPENDIX E: LIST OF ABBREVIATIONS
    # =========================================================================
    doc.add_page_break()
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(12)
    p.paragraph_format.space_after = Pt(4)
    r = p.add_run("APPENDIX E")
    r.bold = True
    r.font.size = Pt(14)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(18)
    r = p.add_run("LIST OF ABBREVIATIONS")
    r.bold = True
    r.font.size = Pt(13)

    abbr_table = doc.add_table(rows=1, cols=3)
    abbr_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    ah = abbr_table.rows[0].cells
    ah[0].width = Inches(0.8)
    ah[1].width = Inches(2.0)
    ah[2].width = Inches(3.0)
    ah[0].paragraphs[0].add_run("S. No.").bold = True
    ah[1].paragraphs[0].add_run("Abbreviation").bold = True
    ah[2].paragraphs[0].add_run("Expansion / Meaning").bold = True

    abbr_data = [
        ("1", "ACID", "Atomicity, Consistency, Isolation, Durability"),
        ("2", "AI", "Artificial Intelligence"),
        ("3", "API", "Application Programming Interface"),
        ("4", "ASV", "Anti-Snake Venom (Polyvalent Serum)"),
        ("5", "BCNF", "Boyce-Codd Normal Form"),
        ("6", "CDN", "Content Delivery Network"),
        ("7", "CRUD", "Create, Read, Update, Delete"),
        ("8", "CSS", "Cascading Style Sheets"),
        ("9", "DAO", "Data Access Object"),
        ("10", "DB", "Database"),
        ("11", "DFD", "Data Flow Diagram"),
        ("12", "DTO", "Data Transfer Object"),
        ("13", "ERD", "Entity Relationship Diagram"),
        ("14", "ETA", "Estimated Time of Arrival"),
        ("15", "GIS", "Geographic Information System"),
        ("16", "HTML", "HyperText Markup Language"),
        ("17", "HTTP", "HyperText Transfer Protocol"),
        ("18", "HTTPS", "HyperText Transfer Protocol Secure"),
        ("19", "ICU", "Intensive Care Unit"),
        ("20", "IP", "Internet Protocol"),
        ("21", "JDK", "Java Development Kit"),
        ("22", "JPA", "Java Persistence API"),
        ("23", "JSON", "JavaScript Object Notation"),
        ("24", "JVM", "Java Virtual Machine"),
        ("25", "JWT", "JSON Web Token"),
        ("26", "LLM", "Large Language Model"),
        ("27", "LTS", "Long Term Support"),
        ("28", "MCA", "Master of Computer Applications"),
        ("29", "NLP", "Natural Language Processing"),
        ("30", "ORM", "Object Relational Mapping"),
        ("31", "ORS", "OpenRouteService"),
        ("32", "PWA", "Progressive Web Application"),
        ("33", "RBAC", "Role-Based Access Control"),
        ("34", "REST", "Representational State Transfer"),
        ("35", "SPA", "Single Page Application"),
        ("36", "SQL", "Structured Query Language"),
        ("37", "SRS", "Software Requirements Specification"),
        ("38", "TLS", "Transport Layer Security"),
        ("39", "TTL", "Time To Live (Expiration Duration)"),
        ("40", "UI", "User Interface"),
        ("41", "URL", "Uniform Resource Locator"),
    ]

    for s, ab, exp in abbr_data:
        row = abbr_table.add_row().cells
        row[0].width = Inches(0.8)
        row[1].width = Inches(2.0)
        row[2].width = Inches(3.0)
        row[0].paragraphs[0].add_run(s)
        row[1].paragraphs[0].add_run(ab).bold = True
        row[2].paragraphs[0].add_run(exp)

    output_path = "SentinelGrid_Mini_Project_Report.docx"
    doc.save(output_path)
    print(f"Report successfully generated at: {os.path.abspath(output_path)}")

if __name__ == "__main__":
    create_document()

