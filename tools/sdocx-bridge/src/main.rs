use std::process::ExitCode;

fn main() -> ExitCode {
    let mut path: Option<String> = None;
    let mut json = false;
    let mut pretty = false;

    for arg in std::env::args().skip(1) {
        match arg.as_str() {
            "--json" | "-j" => json = true,
            "--pretty" | "-p" => {
                json = true;
                pretty = true;
            }
            "-h" | "--help" => {
                usage();
                return ExitCode::SUCCESS;
            }
            other => {
                if path.is_some() {
                    eprintln!("unexpected extra argument: {other}");
                    return ExitCode::from(2);
                }
                path = Some(other.to_string());
            }
        }
    }

    let Some(path) = path else {
        usage();
        return ExitCode::from(2);
    };

    let note = match sdocx_bridge::import_path(&path) {
        Ok(note) => note,
        Err(e) => {
            eprintln!("parse failed: {e}");
            return ExitCode::FAILURE;
        }
    };

    if json {
        let out = if pretty {
            sdocx_bridge::to_json_pretty(&note)
        } else {
            sdocx_bridge::to_json(&note)
        };
        match out {
            Ok(out) => {
                println!("{out}");
                ExitCode::SUCCESS
            }
            Err(e) => {
                eprintln!("serialize failed: {e}");
                ExitCode::FAILURE
            }
        }
    } else {
        print_summary(&note);
        ExitCode::SUCCESS
    }
}

fn print_summary(note: &sdocx_bridge::ImportedNote) {
    println!("title:   {}", note.title);
    println!("created: {}", note.created_ms);
    println!("modified:{}", note.modified_ms);
    println!("pages:   {}", note.pages.len());
    if !note.warnings.is_empty() {
        println!("warnings: {}", note.warnings.join("; "));
    }
    for page in &note.pages {
        println!(
            "\npage {}: {}x{} text={} chars todos={} strokes={} shapes={} lines={} media={}",
            page.index,
            page.width,
            page.height,
            page.text.chars().count(),
            page.todos.len(),
            page.strokes.len(),
            page.shapes.len(),
            page.lines.len(),
            page.media.len()
        );
        if !page.text.trim().is_empty() {
            for line in page.text.lines() {
                println!("  | {line}");
            }
        }
        for todo in &page.todos {
            println!("  [{}] {}", if todo.checked { "x" } else { " " }, todo.text);
        }
        for stroke in &page.strokes {
            println!(
                "  stroke: {} points, color #{:02x}{:02x}{:02x}, width {:.2}",
                stroke.points.len(),
                stroke.color[0],
                stroke.color[1],
                stroke.color[2],
                stroke.width
            );
        }
        for media in &page.media {
            println!(
                "  media: {} ({}) at {:.0},{:.0} {:.0}x{:.0}",
                media.name,
                media.mime_type,
                media.bbox[0],
                media.bbox[1],
                media.bbox[2] - media.bbox[0],
                media.bbox[3] - media.bbox[1]
            );
        }
    }
}

fn usage() {
    eprintln!("usage: sdocx-dump <file.sdocx> [--json] [--pretty]");
    eprintln!("  default: human-readable summary");
    eprintln!("  --json:  import model as JSON");
}
