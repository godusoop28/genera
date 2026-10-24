import { Fragment, type ReactNode } from "react";

/**
 * Markdown sencillo de las respuestas de la IA: títulos, párrafos, listas, tablas con "|",
 * **negritas**, *cursivas*, `código` y ligas. Solo produce elementos de React (nunca HTML crudo).
 */
export function SimpleMarkdown({ text }: { text: string }) {
  const lines = text.replace(/\r/g, "").split("\n");
  const blocks: ReactNode[] = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i].trim();
    if (!line || /^(-{3,}|\*{3,})$/.test(line)) {
      i++;
      continue;
    }
    if (line.startsWith("|")) {
      const rows: string[][] = [];
      while (i < lines.length && lines[i].trim().startsWith("|")) {
        const cells = lines[i]
          .trim()
          .replace(/^\||\|$/g, "")
          .split("|")
          .map((c) => c.trim());
        if (!cells.every((c) => /^:?-{2,}:?$/.test(c) || c === "")) rows.push(cells);
        i++;
      }
      blocks.push(
        <div key={blocks.length} className="my-2 overflow-x-auto">
          <table className="w-full border-collapse text-xs">
            <tbody>
              {rows.map((r, ri) => (
                <tr key={ri} className={ri === 0 ? "bg-brand-ink text-white" : ri % 2 === 0 ? "bg-app-bg" : ""}>
                  {r.map((c, ci) => (
                    <td key={ci} className="border border-border px-2 py-1 align-top">
                      {inline(c)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>,
      );
      continue;
    }
    const heading = /^(#{1,6})\s+(.*)$/.exec(line);
    if (heading) {
      blocks.push(
        <p key={blocks.length} className="mb-1 mt-3 font-semibold text-obsessed">
          {inline(heading[2])}
        </p>,
      );
      i++;
      continue;
    }
    if (/^([-*•]|\d+[.)])\s+/.test(line)) {
      const ordered = /^\d+[.)]\s+/.test(line);
      const items: string[] = [];
      while (i < lines.length && /^\s*([-*•]|\d+[.)])\s+/.test(lines[i])) {
        items.push(lines[i].trim().replace(/^([-*•]|\d+[.)])\s+/, ""));
        i++;
      }
      const List = ordered ? "ol" : "ul";
      blocks.push(
        <List key={blocks.length} className={`my-1.5 ml-5 ${ordered ? "list-decimal" : "list-disc"} space-y-0.5`}>
          {items.map((it, k) => (
            <li key={k}>{inline(it)}</li>
          ))}
        </List>,
      );
      continue;
    }
    const paragraph: string[] = [];
    while (i < lines.length && lines[i].trim() && !/^(#{1,6}\s|\||[-*•]\s|\d+[.)]\s)/.test(lines[i].trim())) {
      paragraph.push(lines[i].trim());
      i++;
    }
    blocks.push(
      <p key={blocks.length} className="my-1.5">
        {inline(paragraph.join(" "))}
      </p>,
    );
  }
  return <div className="text-sm leading-relaxed text-obsessed">{blocks}</div>;
}

const INLINE = /\*\*(.+?)\*\*|\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)|(https?:\/\/[^\s)\]]+)|\*(?!\s)([^*]+?)\*|`([^`]+)`/g;

function inline(text: string): ReactNode {
  const out: ReactNode[] = [];
  let last = 0;
  for (const m of text.matchAll(INLINE)) {
    const start = m.index ?? 0;
    if (start > last) out.push(text.slice(last, start));
    if (m[1]) out.push(<strong key={start}>{m[1]}</strong>);
    else if (m[2]) out.push(link(start, m[2], m[3]));
    else if (m[4]) out.push(link(start, m[4].replace(/^https?:\/\/(www\.)?/, "").slice(0, 50), m[4]));
    else if (m[5]) out.push(<em key={start}>{m[5]}</em>);
    else if (m[6]) out.push(<code key={start} className="rounded bg-app-bg px-1 text-xs">{m[6]}</code>);
    last = start + m[0].length;
  }
  if (last < text.length) out.push(text.slice(last));
  return <Fragment>{out}</Fragment>;
}

function link(key: number, label: string, href: string) {
  return (
    <a key={key} href={href} target="_blank" rel="noopener noreferrer" className="text-info-text underline">
      {label}
    </a>
  );
}
