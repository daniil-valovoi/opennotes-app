//! Thin wrapper around the `sdocx` crate.
//!
//! Turns a Samsung Notes `.sdocx` archive into the flat import model the Android app
//! stores: a title, timestamps, and per-page text, todos, strokes and media names.
//!
//! The same code backs the `sdocx-dump` CLI used to check fidelity on the Mac.

use serde::Serialize;
use sdocx::{BulletType, NativeShape, PageElement, PlacedImage, RichTextObjectContent, RichTextBox, ShapePaint};

#[cfg(target_os = "android")]
mod jni;

/// Fallback when the archive carries no page size.
const FALLBACK_PAGE_SIZE: (u32, u32) = (1080, 1527);

#[derive(Debug, Clone, Serialize)]
pub struct ImportedNote {
    pub title: String,
    pub created_ms: i64,
    pub modified_ms: i64,
    pub pages: Vec<ImportedPage>,
    pub warnings: Vec<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct ImportedPage {
    pub index: usize,
    pub width: u32,
    pub height: u32,
    /// Typed text belonging to this page, paragraphs separated by `\n`.
    /// Checkbox paragraphs are excluded here and reported in `todos`.
    pub text: String,
    /// Checkbox paragraphs on this page, in reading order.
    pub todos: Vec<TodoItem>,
    pub strokes: Vec<ImportedStroke>,
    /// Basic geometric shapes, drawn into the page image.
    pub shapes: Vec<ImportedShape>,
    pub lines: Vec<ImportedLine>,
    /// Images placed on this page, including their archive path.
    pub media: Vec<ImportedMedia>,
}

/// Shape templates the renderer understands: 1 ellipse, 2 triangle, 3 right
/// triangle, 4 rectangle, 8 diamond. Custom native paths are not decoded.
#[derive(Debug, Clone, Serialize)]
pub struct ImportedShape {
    pub kind: u8,
    /// `[x_min, y_min, x_max, y_max]` in page coordinates.
    pub bbox: [f32; 4],
    pub rotation: f32,
    /// Solid colors as ARGB, `None` when absent or unsupported.
    pub stroke: Option<u32>,
    pub fill: Option<u32>,
    pub width: f32,
}

#[derive(Debug, Clone, Serialize)]
pub struct ImportedLine {
    pub begin: [f32; 2],
    pub end: [f32; 2],
    /// Polyline for elbow/curve lines, when present.
    pub points: Vec<[f32; 2]>,
    /// ARGB.
    pub color: u32,
    pub width: f32,
}

#[derive(Debug, Clone, Serialize)]
pub struct TodoItem {
    pub text: String,
    pub checked: bool,
}

#[derive(Debug, Clone, Serialize)]
pub struct ImportedStroke {
    pub color: [u8; 3],
    pub width: f32,
    pub points: Vec<[f32; 2]>,
    pub pressures: Vec<f32>,
}

#[derive(Debug, Clone, Serialize)]
pub struct ImportedMedia {
    /// Archive path, e.g. `media/0@page_0000006.spi`.
    pub name: String,
    pub mime_type: String,
    /// Placement box in page coordinates: `[x_min, y_min, x_max, y_max]`.
    pub bbox: [f32; 4],
}

/// One paragraph of the document body, with its start offset in UTF-16 units.
struct Line {
    offset: usize,
    text: String,
}

/// Resource limits for untrusted archives. Phones cannot afford the crate defaults
/// (10k pages, 2 GiB total), so cap everything an exported note can plausibly need.
fn import_options(verify_integrity: bool) -> sdocx::ParseOptions {
    sdocx::ParseOptions {
        verify_integrity,
        limits: sdocx::ParseLimits {
            max_archive_entries: 4_096,
            max_entry_size: 64 * 1024 * 1024,
            max_total_uncompressed_size: 512 * 1024 * 1024,
            max_pages: 2_000,
            max_strokes_per_page: 20_000,
            max_points_per_stroke: 20_000,
            max_objects_per_page: 20_000,
            max_layers_per_page: 16,
            max_object_nesting_depth: 32,
            ..Default::default()
        },
        ..Default::default()
    }
}

/// Parse a `.sdocx` byte buffer into the import model.
pub fn import_bytes(bytes: &[u8]) -> Result<ImportedNote, String> {
    // Verify checksums when the export carries them. Files edited by some Samsung
    // Notes versions have stale hashes, so fall back to a plain parse rather than
    // refusing an otherwise readable note.
    let integrity_error = match sdocx::parse_bytes_detailed_with_options(bytes, &import_options(true))
    {
        Ok(parsed) => {
            return Ok(build_note(parsed, true));
        }
        Err(error) => format!("{error:?}"),
    };
    match sdocx::parse_bytes_detailed_with_options(bytes, &import_options(false)) {
        Ok(parsed) => Ok(build_note(parsed, false)),
        Err(_) => Err(integrity_error),
    }
}

fn build_note(parsed: sdocx::ParsedDocument, integrity_verified: bool) -> ImportedNote {
    let document = &parsed.document;
    let mut warnings = Vec::new();
    if !integrity_verified {
        warnings.push("archive checksums could not be verified".to_string());
    }

    // Title and body live in note.note, not in the page objects.
    let (raw_title, body) = match &parsed.note {
        Some(note) => (note.title.text.clone(), Some(&note.body)),
        None => {
            warnings.push("note.note missing: no typed text recovered".to_string());
            (String::new(), None)
        }
    };
    let body_text = body.map(|b| b.text.clone()).unwrap_or_default();
    let body_paragraphs = body.map(|b| b.paragraphs.clone()).unwrap_or_default();
    let body_sections = body.map(|b| b.text_sections.clone()).unwrap_or_default();

    let lines = split_lines(&body_text);
    let title = if raw_title.trim().is_empty() {
        lines
            .first()
            .map(|line| line.text.trim().to_string())
            .unwrap_or_default()
    } else {
        raw_title.trim().to_string()
    };

    // Samsung Notes keeps the title as the first body paragraph. Keep it in the page
    // text as well so the editor shows the note exactly as it was written.

    // Checkbox paragraph ranges: start line, end line (exclusive), checked state.
    let mut checkers: Vec<(usize, usize, bool)> = Vec::new();
    for paragraph in &body_paragraphs {
        let Some(bullet) = paragraph.bullet() else {
            continue;
        };
        if bullet.kind != BulletType::Checker {
            continue;
        }
        let start = (paragraph.start_paragraph as usize).min(lines.len());
        let end = (paragraph.end_paragraph as usize).max(start + 1).min(lines.len());
        if start < end {
            checkers.push((start, end, bullet.checked));
        }
    }

    let mut covered = vec![false; lines.len()];
    for &(start, end, _) in &checkers {
        for flag in covered.iter_mut().take(end).skip(start) {
            *flag = true;
        }
    }

    // Page text ranges, in UTF-16 units. Older files may omit them.
    let sections: Vec<(usize, usize)> = body_sections
        .iter()
        .filter(|s| s.start_utf16 >= 0 && s.length_utf16 >= 0)
        .map(|s| (s.start_utf16 as usize, s.length_utf16 as usize))
        .collect();
    let ranges_from_sections = !sections.is_empty();

    let page_count = document
        .pages
        .len()
        .max(sections.len())
        .max(if body_text.trim().is_empty() { 0 } else { 1 });
    let fallback_size = document
        .metadata
        .page_dimensions
        .or(document.metadata.default_page_dimensions)
        .unwrap_or(FALLBACK_PAGE_SIZE);

    let mut pages = Vec::with_capacity(page_count);

    for index in 0..page_count {
        let page = document.pages.get(index);
        let (width, height) = page
            .map(|p| (p.width, p.height))
            .filter(|&(w, h)| w > 0 && h > 0)
            .unwrap_or(fallback_size);

        let range = match sections.get(index) {
            Some(&(start, len)) => start..start.saturating_add(len),
            None if index == 0 && !ranges_from_sections => 0..usize::MAX,
            None => 0..0,
        };

        let mut paragraph_texts: Vec<String> = Vec::new();
        let mut todos: Vec<TodoItem> = Vec::new();

        if ranges_from_sections || index == 0 {
            for (line_index, line) in lines.iter().enumerate() {
                if line.offset < range.start || line.offset >= range.end {
                    continue;
                }
                if !covered.get(line_index).copied().unwrap_or(false) {
                    paragraph_texts.push(line.text.clone());
                }
            }

            for &(start, end, checked) in &checkers {
                let Some(first) = lines.get(start) else {
                    continue;
                };
                if first.offset < range.start || first.offset >= range.end {
                    continue;
                }
                let text = lines[start..end]
                    .iter()
                    .map(|line| line.text.trim())
                    .collect::<Vec<_>>()
                    .join("\n")
                    .trim()
                    .to_string();
                if !text.is_empty() {
                    todos.push(TodoItem { text, checked });
                }
            }
        }

        // Floating text and shapes carry their own text; keep the characters even though
        // their exact position cannot be reproduced.
        let mut shapes = Vec::new();
        let mut lines = Vec::new();
        if let Some(page) = page {
            for element in page.elements() {
                match element {
                    PageElement::TextBox(text_box) => append_rich_text(&mut paragraph_texts, text_box),
                    PageElement::Shape(shape) => {
                        if let Some(text_box) = &shape.text {
                            append_rich_text(&mut paragraph_texts, text_box);
                        }
                        match simplify_shape(shape) {
                            Some(shape) => shapes.push(shape),
                            None => warnings.push(format!(
                                "page {index}: one shape could not be rendered (unsupported template)"
                            )),
                        }
                    }
                    PageElement::Line(line) => lines.push(ImportedLine {
                        begin: [line.begin[0] as f32, line.begin[1] as f32],
                        end: [line.end[0] as f32, line.end[1] as f32],
                        points: line
                            .control_points
                            .iter()
                            .map(|p| [p[0] as f32, p[1] as f32])
                            .collect(),
                        color: solid_color(&line.style.paint).unwrap_or(0xff00_0000),
                        width: line.style.width,
                    }),
                    PageElement::Image { .. } | PageElement::PlacedImage(_) => {}
                    _ => {}
                }
            }
        }

        // Tables, code blocks and body images are embedded in flowing text.
        if let Some(body) = body {
            for span in &body.object_spans {
                let span_page = if ranges_from_sections {
                    sections
                        .iter()
                        .position(|&(start, len)| {
                            let index = span.text_index_utf16.max(0) as usize;
                            index >= start && index < start.saturating_add(len)
                        })
                        .unwrap_or(0)
                } else {
                    0
                };
                let Some(content) = &span.content else {
                    continue;
                };
                match content {
                    RichTextObjectContent::Table(table) => {
                        if span_page != index {
                            continue;
                        }
                        let text = table_text(table);
                        if !text.is_empty() {
                            paragraph_texts.push(text);
                            warnings.push(format!(
                                "page {index}: table converted to plain text"
                            ));
                        }
                    }
                    RichTextObjectContent::CodeBlock(block) => {
                        if span_page != index {
                            continue;
                        }
                        let mut parts = Vec::new();
                        if let Some(title) = &block.title {
                            append_rich_text(&mut parts, title);
                        }
                        if let Some(body) = &block.body {
                            append_rich_text(&mut parts, body);
                        }
                        paragraph_texts.extend(parts);
                    }
                    // Body images are collected in the media pass below, in page order.
                    RichTextObjectContent::Image(_) => {}
                    _ => {}
                }
            }
        }

        let mut strokes = Vec::new();
        if let Some(page) = page {
            for stroke in page.strokes() {
                if stroke.points.is_empty() {
                    continue;
                }
                let color = stroke.color.unwrap_or(sdocx::Color { r: 0, g: 0, b: 0 });
                strokes.push(ImportedStroke {
                    color: [color.r, color.g, color.b],
                    width: stroke.pen_width,
                    points: stroke
                        .points
                        .iter()
                        .map(|p| [p.x as f32, p.y as f32])
                        .collect(),
                    pressures: stroke.pressures.iter().map(|&p| p as f32).collect(),
                });
            }
        }

        let mut media = Vec::new();
        if let Some(page) = page {
            for element in page.elements() {
                if let PageElement::Image { bbox, media_index } = element {
                    if let Some(asset) = document.metadata.media_assets.get(*media_index) {
                        media.push(ImportedMedia {
                            name: asset.name.clone(),
                            mime_type: asset.mime_type.clone(),
                            bbox: [
                                bbox.x_min as f32,
                                bbox.y_min as f32,
                                bbox.x_max as f32,
                                bbox.y_max as f32,
                            ],
                        });
                    } else {
                        warnings.push(format!(
                            "page {index}: image references missing asset #{media_index}"
                        ));
                    }
                } else if let PageElement::PlacedImage(placed) = element {
                    collect_media(&mut media, placed, document, index, &mut warnings);
                }
            }
        }
        if let Some(body) = body {
            for span in &body.object_spans {
                let span_page = if ranges_from_sections {
                    sections
                        .iter()
                        .position(|&(start, len)| {
                            let index = span.text_index_utf16.max(0) as usize;
                            index >= start && index < start.saturating_add(len)
                        })
                        .unwrap_or(0)
                } else {
                    0
                };
                if span_page != index {
                    continue;
                }
                if let Some(RichTextObjectContent::Image(image)) = &span.content {
                    collect_media(&mut media, image, document, index, &mut warnings);
                }
            }
        }

        // Samsung Notes pads the area under the title with empty paragraphs. Drop the
        // ones directly after it so the note does not open with a large gap.
        if paragraph_texts.first().is_some_and(|line| line.trim() == title) {
            while paragraph_texts.len() > 1 && paragraph_texts[1].trim().is_empty() {
                paragraph_texts.remove(1);
            }
        }

        let text = paragraph_texts.join("\n").trim_matches('\n').trim_end().to_string();

        pages.push(ImportedPage {
            index,
            width,
            height,
            text,
            todos,
            strokes,
            shapes,
            lines,
            media,
        });
    }

    // Layout drops pages that hold nothing; keep only content pages.
    pages.retain(|p| {
        !p.text.trim().is_empty()
            || !p.todos.is_empty()
            || !p.strokes.is_empty()
            || !p.shapes.is_empty()
            || !p.lines.is_empty()
            || !p.media.is_empty()
    });
    for (index, page) in pages.iter_mut().enumerate() {
        page.index = index;
    }
    if pages.is_empty() {
        pages.push(ImportedPage {
            index: 0,
            width: fallback_size.0,
            height: fallback_size.1,
            text: String::new(),
            todos: Vec::new(),
            strokes: Vec::new(),
            shapes: Vec::new(),
            lines: Vec::new(),
            media: Vec::new(),
        });
    }

    if pages
        .iter()
        .all(|p| p.text.trim().is_empty() && p.strokes.is_empty())
    {
        warnings.push("no text and no strokes recovered".to_string());
    }

    let header = parsed.note.as_ref().map(|n| &n.header);
    let created_ms = document
        .metadata
        .created_ms
        .or_else(|| header.map(|h| h.created_time_raw))
        .unwrap_or(0);
    let modified_ms = document
        .metadata
        .modified_ms
        .or_else(|| header.map(|h| h.modified_time_raw))
        .unwrap_or(created_ms);

    ImportedNote {
        title,
        created_ms,
        modified_ms,
        pages,
        warnings,
    }
}

/// Parse a `.sdocx` file on disk into the import model.
pub fn import_path(path: &str) -> Result<ImportedNote, String> {
    let bytes = std::fs::read(path).map_err(|e| format!("read {path}: {e}"))?;
    import_bytes(&bytes)
}

/// Serialize the import model to JSON.
pub fn to_json(note: &ImportedNote) -> Result<String, String> {
    serde_json::to_string(note).map_err(|e| format!("{e:?}"))
}

/// Serialize the import model to indented JSON.
pub fn to_json_pretty(note: &ImportedNote) -> Result<String, String> {
    serde_json::to_string_pretty(note).map_err(|e| format!("{e:?}"))
}

/// Append a rich text box's paragraphs, dropping the U+FFFC object placeholders.
fn append_rich_text(out: &mut Vec<String>, text_box: &RichTextBox) {
    let text = text_box.text.replace('\u{fffc}', "");
    for line in text.split('\n') {
        let line = line.trim_end();
        if !line.is_empty() {
            out.push(line.to_string());
        }
    }
}

/// Flatten a table to text: one row per line, cells separated by ` | `.
fn table_text(table: &sdocx::RichTextTable) -> String {
    let mut rows = Vec::new();
    for row in &table.rows {
        let mut cells = Vec::new();
        for cell in &row.cells {
            let text = cell.content.text.replace('\u{fffc}', "");
            let text = text.trim().replace('\n', " ");
            if !text.is_empty() {
                cells.push(text);
            }
        }
        if !cells.is_empty() {
            rows.push(cells.join(" | "));
        }
    }
    rows.join("\n")
}

/// Keep only shape templates the app can draw without the crate's renderer.
fn simplify_shape(shape: &NativeShape) -> Option<ImportedShape> {
    if !shape.path_data.is_empty() {
        return None;
    }
    let kind = match shape.shape_type {
        1..=4 | 8 => shape.shape_type as u8,
        _ => return None,
    };
    let bbox = &shape.geometry_bbox;
    if bbox.x_max <= bbox.x_min || bbox.y_max <= bbox.y_min {
        return None;
    }
    Some(ImportedShape {
        kind,
        bbox: [
            bbox.x_min as f32,
            bbox.y_min as f32,
            bbox.x_max as f32,
            bbox.y_max as f32,
        ],
        rotation: shape.rotation_degrees,
        stroke: solid_color(&shape.style.paint),
        fill: solid_color(&shape.fill),
        width: shape.style.width,
    })
}

fn solid_color(paint: &ShapePaint) -> Option<u32> {
    match paint {
        ShapePaint::Solid(argb) => Some(*argb),
        _ => None,
    }
}

fn collect_media(
    out: &mut Vec<ImportedMedia>,
    placed: &PlacedImage,
    document: &sdocx::Document,
    index: usize,
    warnings: &mut Vec<String>,
) {
    let Some(media_index) = placed.media_index else {
        warnings.push(format!("page {index}: placed image has no resolved media asset"));
        return;
    };
    let Some(asset) = document.metadata.media_assets.get(media_index) else {
        warnings.push(format!(
            "page {index}: image references missing asset #{media_index}"
        ));
        return;
    };
    let bbox = &placed.bbox;
    out.push(ImportedMedia {
        name: asset.name.clone(),
        mime_type: asset.mime_type.clone(),
        bbox: [
            bbox.x_min as f32,
            bbox.y_min as f32,
            bbox.x_max as f32,
            bbox.y_max as f32,
        ],
    });
}

/// Split text into lines, recording each line's start offset in UTF-16 units.
fn split_lines(text: &str) -> Vec<Line> {
    let mut out = Vec::new();
    let mut offset = 0usize;
    for line in text.split('\n') {
        out.push(Line {
            offset,
            text: line.to_string(),
        });
        offset += line.encode_utf16().count() + 1;
    }
    out
}
