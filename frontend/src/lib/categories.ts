export const CATEGORY_ORDER = ["cpu", "memory", "security", "stability", "concurrency", "database"];

export const CATEGORY_META: Record<string, { icon: string; label: string; desc: string }> = {
  cpu: { icon: "⚡", label: "CPU Core", desc: "Instruction pipelining & event-loop scheduling." },
  memory: { icon: "🧠", label: "Virtual Memory", desc: "Cache coherency & heap management." },
  security: { icon: "🔒", label: "Vault Security", desc: "Injection, traversal & access-control defense." },
  stability: { icon: "🛡️", label: "Runtime Stability", desc: "Crash recovery & graceful error handling." },
  concurrency: { icon: "🔀", label: "Concurrency", desc: "Race conditions & atomic operations." },
  database: { icon: "🗄️", label: "Database", desc: "Query efficiency & transactional integrity." },
};

export function categoryMeta(category: string) {
  return CATEGORY_META[category] || { icon: "📦", label: category, desc: "" };
}

export function tierFor(mastery: number) {
  if (mastery >= 75) return { label: "Expert", key: "expert" as const, color: "var(--k-primary)" };
  if (mastery >= 40) return { label: "Competent", key: "competent" as const, color: "var(--k-accent-amber)" };
  if (mastery > 0) return { label: "Novice", key: "novice" as const, color: "var(--k-text-muted)" };
  return { label: "Unranked", key: "unranked" as const, color: "var(--k-border)" };
}
