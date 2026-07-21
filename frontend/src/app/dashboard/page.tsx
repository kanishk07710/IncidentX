"use client";
import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { apiFetch, apiFetchWithRetry } from "@/lib/api";
import { CATEGORY_ORDER, CATEGORY_META, tierFor } from "@/lib/categories";
import type { CurrentUser } from "@/lib/types";
import UserProfileMenu from "@/components/UserProfileMenu";
import styles from "./dashboard.module.css";

interface Incident {
  id: string;
  title: string;
  difficulty: string;
  category: string;
}

interface UserProfile {
  rating: number;
  attemptsCount: number;
  solvedCount: number;
  categoryMasteryJson: string;
  solvedIncidentsJson: string;
}

const THEME_KEY = "incidentx_dashboard_theme";

const TIER_CLASS: Record<string, string> = {
  expert: styles.tierExpert,
  competent: styles.tierCompetent,
  novice: styles.tierNovice,
  unranked: styles.tierUnranked,
};

function polar(index: number, total: number, radius: number, cx = 100, cy = 100) {
  const angle = (Math.PI * 2 * index) / total - Math.PI / 2;
  return { x: cx + radius * Math.cos(angle), y: cy + radius * Math.sin(angle) };
}

export default function DashboardPage() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [wakingUp, setWakingUp] = useState(false);
  const [search, setSearch] = useState("");
  const [theme, setTheme] = useState<"light" | "dark">("light");
  const [reloadKey, setReloadKey] = useState(0);
  const router = useRouter();

  useEffect(() => {
    function init() {
      const saved = window.localStorage.getItem(THEME_KEY);
      if (saved === "dark" || saved === "light") setTheme(saved);
    }
    init();
  }, []);

  function changeTheme(next: "light" | "dark") {
    setTheme(next);
    window.localStorage.setItem(THEME_KEY, next);
  }

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setLoadError(false);
      const onRetry = () => setWakingUp(true);

      try {
        const [incRes, profRes, userRes] = await Promise.all([
          apiFetchWithRetry("/api/incidents", {}, onRetry),
          apiFetchWithRetry("/api/profiles/me", {}, onRetry),
          apiFetchWithRetry("/api/auth/me", {}, onRetry),
        ]);
        if (cancelled) return;

        if (incRes.status === 401 || profRes.status === 401 || userRes.status === 401) {
          router.push("/");
          return;
        }

        // All three or none — a lone failure here used to be swallowed silently (only the
        // successful pieces got applied), which is exactly what let the dashboard render with
        // a stale/default user, profile, or incident count instead of failing loudly. Treat any
        // non-2xx as a load failure and surface a retry instead of rendering partial state.
        if (!incRes.ok || !profRes.ok || !userRes.ok) {
          setLoadError(true);
          return;
        }

        const [incidentsData, profileData, userData] = await Promise.all([
          incRes.json(),
          profRes.json(),
          userRes.json(),
        ]);
        if (cancelled) return;

        setIncidents(incidentsData);
        setProfile(profileData);
        setUser(userData);
      } catch {
        if (!cancelled) setLoadError(true);
      } finally {
        if (!cancelled) {
          setWakingUp(false);
          setLoading(false);
        }
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [router, reloadKey]);

  async function handleLogout() {
    await apiFetch("/api/auth/logout", { method: "POST" });
    router.push("/");
  }

  const mastery: Record<string, number> = useMemo(() => {
    try {
      return profile?.categoryMasteryJson ? JSON.parse(profile.categoryMasteryJson) : {};
    } catch {
      return {};
    }
  }, [profile]);

  const solvedIds: string[] = useMemo(() => {
    try {
      return profile?.solvedIncidentsJson ? JSON.parse(profile.solvedIncidentsJson) : [];
    } catch {
      return [];
    }
  }, [profile]);

  const categoriesPresent = useMemo(() => {
    const set = new Set(incidents.map((i) => i.category));
    return CATEGORY_ORDER.filter((c) => set.has(c) || CATEGORY_META[c]);
  }, [incidents]);

  const overallMastery = useMemo(() => {
    if (categoriesPresent.length === 0) return 0;
    const sum = categoriesPresent.reduce((acc, c) => acc + (mastery[c] || 0), 0);
    return Math.round(sum / categoriesPresent.length);
  }, [categoriesPresent, mastery]);

  const featuredCategory = useMemo(() => {
    if (categoriesPresent.length === 0) return null;
    return [...categoriesPresent].sort((a, b) => (mastery[a] || 0) - (mastery[b] || 0))[0];
  }, [categoriesPresent, mastery]);

  const nextIncident = useMemo(
    () => incidents.find((i) => !solvedIds.includes(i.id)) || null,
    [incidents, solvedIds]
  );

  const filteredIncidents = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return incidents;
    return incidents.filter(
      (i) => i.title.toLowerCase().includes(q) || i.category.toLowerCase().includes(q)
    );
  }, [incidents, search]);

  const passRate =
    profile && profile.attemptsCount > 0
      ? Math.round((profile.solvedCount / profile.attemptsCount) * 100)
      : null;

  if (loading) {
    return (
      <main className={styles.loadingContainer}>
        <div className="spinner" />
        <p>
          {wakingUp
            ? "Waking up the server — this can take up to a minute after a period of inactivity…"
            : "Loading systems overview..."}
        </p>
      </main>
    );
  }

  if (loadError || !user || !profile) {
    // The !user / !profile check is a type-narrowing safety net, not expected to trigger in
    // practice: `load()` above only clears `loading` after incidents, profile, and user have
    // all been set together, so this should be unreachable — but it means a null user can never
    // silently fall through to a fabricated "Operator" placeholder further down.
    return (
      <main className={styles.loadingContainer}>
        <p>Couldn&rsquo;t load your dashboard.</p>
        <button className="btn btn-primary" onClick={() => setReloadKey((k) => k + 1)}>
          Try Again
        </button>
      </main>
    );
  }

  return (
    <div className={styles.shell} data-theme={theme}>
      {/* Sidebar */}
      <aside className={styles.sidebar}>
        <div className={styles.brandRow}>
          <span className={styles.brandIcon}>⚡</span>
          <span className={styles.brandName}>IncidentX</span>
        </div>
        <div className={styles.brandVersion}>Practice Mode</div>

        <nav className={styles.nav}>
          <Link href="/dashboard" className={`${styles.navLink} ${styles.navLinkActive}`}>
            <span className={styles.navIcon}>▦</span>
            Dashboard
          </Link>
          {categoriesPresent.map((cat) => (
            <Link key={cat} href={`/category/${cat}`} className={styles.navLink}>
              <span className={styles.navIcon}>{CATEGORY_META[cat]?.icon || "📦"}</span>
              {CATEGORY_META[cat]?.label || cat}
            </Link>
          ))}
        </nav>

        <div className={styles.sidebarBottom}>
          <div className={styles.masteryCard}>
            <div className={styles.masteryCardLabel}>Overall Mastery</div>
            <div className={styles.masteryCardValueRow}>
              <span className={styles.masteryCardValue}>
                {overallMastery}
                <span>%</span>
              </span>
            </div>
            <div className={styles.masteryCardTrack}>
              <div className={styles.masteryCardFill} style={{ width: `${overallMastery}%` }} />
            </div>
          </div>

          {nextIncident ? (
            <Link href={`/incident/${nextIncident.id}`} className={styles.continueBtn}>
              🚀 Continue Practicing
            </Link>
          ) : incidents.length > 0 ? (
            <div className={styles.continueBtn} style={{ cursor: "default" }}>
              🏆 All Solved
            </div>
          ) : null}

          <UserProfileMenu user={user} onLogout={handleLogout} />
        </div>
      </aside>

      {/* Content */}
      <div className={styles.contentArea}>
        <header className={styles.topbar}>
          <div className={styles.searchWrap}>
            <span className={styles.searchIcon}>🔍</span>
            <input
              className={styles.searchInput}
              placeholder="Search incidents by title or category…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          <div className={styles.topbarRight}>
            <div className={styles.statCluster}>
              <div className={styles.statMini}>
                <span className={styles.statMiniLabel}>Solved</span>
                <span className={styles.statMiniValue}>
                  {profile?.solvedCount ?? 0}/{incidents.length}
                </span>
              </div>
              <div className={styles.statMini}>
                <span className={styles.statMiniLabel}>Attempts</span>
                <span className={styles.statMiniValue}>{profile?.attemptsCount ?? 0}</span>
              </div>
              <div className={styles.statMini}>
                <span className={styles.statMiniLabel}>Pass Rate</span>
                <span className={styles.statMiniValue}>{passRate !== null ? `${passRate}%` : "—"}</span>
              </div>
            </div>

            <div className={styles.themeToggle}>
              <button
                className={`${styles.themeBtn} ${theme === "light" ? styles.themeBtnActive : ""}`}
                onClick={() => changeTheme("light")}
                title="Light mode"
              >
                ☀️
              </button>
              <button
                className={`${styles.themeBtn} ${theme === "dark" ? styles.themeBtnActive : ""}`}
                onClick={() => changeTheme("dark")}
                title="Dark mode"
              >
                🌙
              </button>
            </div>
          </div>
        </header>

        <main className={styles.main}>
          <div className={styles.pageHeader}>
            <div>
              <h1 className={styles.pageTitle}>Systems Overview</h1>
              <p className={styles.pageSubtitle}>
                Deep-cycle performance analysis across {categoriesPresent.length} incident categories.
              </p>
            </div>
            <div className={styles.focusBadge}>
              <span className={styles.focusDot} />
              {featuredCategory ? `${CATEGORY_META[featuredCategory]?.label} Focus` : "No Focus Set"}
            </div>
          </div>

          {/* Category grid */}
          <div className={styles.categoryGrid}>
            {categoriesPresent.map((cat) => {
              const catIncidents = incidents.filter((i) => i.category === cat);
              const solvedInCat = catIncidents.filter((i) => solvedIds.includes(i.id)).length;
              const val = mastery[cat] || 0;
              const tier = tierFor(val);
              const meta = CATEGORY_META[cat] || { icon: "📦", label: cat, desc: "" };
              const isFeatured = cat === featuredCategory;

              return (
                <Link
                  key={cat}
                  href={`/category/${cat}`}
                  className={`${styles.categoryCard} ${isFeatured ? styles.categoryCardFeatured : ""}`}
                >
                  <div className={styles.categoryTop}>
                    <div className={styles.categoryIconWrap}>{meta.icon}</div>
                    <span className={`${styles.tierBadge} ${TIER_CLASS[tier.key]}`}>{tier.label}</span>
                  </div>
                  <div className={styles.categoryName}>{meta.label}</div>
                  <div className={styles.categoryDesc}>{meta.desc}</div>

                  <div className={styles.categoryMasteryRow}>
                    <span className={styles.categoryMasteryLabel}>MASTERY</span>
                    <span className={styles.categoryMasteryValue}>{val}%</span>
                  </div>
                  <div className={styles.categoryTrack}>
                    <div
                      className={styles.categoryFill}
                      style={{ width: `${val}%`, background: tier.color }}
                    />
                  </div>

                  <div className={styles.categoryFooter}>
                    <span className={styles.categorySolvedCount}>
                      {solvedInCat}/{catIncidents.length} solved
                    </span>
                    <span className={styles.categoryArrow}>→</span>
                  </div>
                </Link>
              );
            })}
          </div>

          {/* Radar + quick list */}
          <div className={styles.splitGrid}>
            <section className={styles.panel}>
              <div className={styles.panelHeader}>
                <div className={styles.panelTitle}>Competency Matrix</div>
                <div className={styles.panelSubtitle}>Mastery across every category</div>
              </div>
              <div className={styles.radarWrap}>
                <svg width="280" height="280" viewBox="-35 -20 270 240">
                  {[20, 40, 60, 80].map((r) => (
                    <circle
                      key={r}
                      cx={100}
                      cy={100}
                      r={r}
                      stroke="var(--k-border)"
                      strokeDasharray="2 4"
                      fill="none"
                    />
                  ))}
                  {categoriesPresent.map((cat, i) => {
                    const p = polar(i, categoriesPresent.length, 80);
                    return (
                      <line
                        key={cat}
                        x1={100}
                        y1={100}
                        x2={p.x}
                        y2={p.y}
                        stroke="var(--k-border)"
                      />
                    );
                  })}
                  <polygon
                    points={categoriesPresent
                      .map((cat, i) => {
                        const p = polar(i, categoriesPresent.length, 10 + ((mastery[cat] || 0) / 100) * 70);
                        return `${p.x},${p.y}`;
                      })
                      .join(" ")}
                    fill="var(--k-accent-purple)"
                    fillOpacity="0.18"
                    stroke="var(--k-accent-purple)"
                    strokeWidth="2"
                  />
                  {categoriesPresent.map((cat, i) => {
                    const labelPoint = polar(i, categoriesPresent.length, 92);
                    return (
                      <text
                        key={cat}
                        x={labelPoint.x}
                        y={labelPoint.y}
                        textAnchor="middle"
                        dominantBaseline="middle"
                        fontSize="7"
                        fontWeight="800"
                        fill="var(--k-text-secondary)"
                      >
                        {(CATEGORY_META[cat]?.label || cat).toUpperCase()}
                      </text>
                    );
                  })}
                </svg>
              </div>
              <div className={styles.radarLegend}>
                <span>
                  <span
                    className={styles.legendSwatch}
                    style={{ background: "var(--k-accent-purple)" }}
                  />
                  Your Mastery
                </span>
              </div>
            </section>

            <section className={styles.panel}>
              <div className={styles.panelHeader}>
                <div className={styles.panelTitle}>All Incidents</div>
                <div className={styles.panelSubtitle}>
                  {search ? `${filteredIncidents.length} matching "${search}"` : `${incidents.length} total`}
                </div>
              </div>
              <div className={styles.quickList}>
                {filteredIncidents.length === 0 ? (
                  <p className={styles.emptyText}>No incidents match your search.</p>
                ) : (
                  filteredIncidents.map((inc) => {
                    const solved = solvedIds.includes(inc.id);
                    const diffClass =
                      inc.difficulty === "EASY"
                        ? styles.diffEasy
                        : inc.difficulty === "MEDIUM"
                        ? styles.diffMedium
                        : styles.diffHard;
                    return (
                      <Link key={inc.id} href={`/incident/${inc.id}`} className={styles.quickItem}>
                        <span className={`${styles.quickSolved} ${solved ? styles.quickSolvedDone : ""}`}>
                          {solved ? "✓" : ""}
                        </span>
                        <div className={styles.quickInfo}>
                          <div className={styles.quickTitle}>{inc.title}</div>
                          <div className={styles.quickMeta}>
                            {CATEGORY_META[inc.category]?.icon} {inc.category}
                          </div>
                        </div>
                        <span className={`${styles.quickDifficulty} ${diffClass}`}>{inc.difficulty}</span>
                      </Link>
                    );
                  })
                )}
              </div>
            </section>
          </div>
        </main>
      </div>
    </div>
  );
}
