"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { apiFetch } from "@/lib/api";
import styles from "./dashboard.module.css";

interface Incident {
  id: string;
  title: string;
  difficulty: string;
  category: string;
}

interface UserProfile {
  attemptsCount: number;
  solvedCount: number;
  rating: number;
  categoryMasteryJson: string;
}

const categoryIcons: Record<string, string> = {
  memory: "🧠",
  cpu: "⚡",
  security: "🔒",
  stability: "🛡️",
  database: "🗄️",
  concurrency: "🔀",
};

export default function DashboardPage() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const router = useRouter();

  useEffect(() => {
    async function load() {
      try {
        const [incRes, profRes] = await Promise.all([
          apiFetch("/api/incidents"),
          apiFetch("/api/profiles/me"),
        ]);

        if (incRes.status === 401 || profRes.status === 401) {
          router.push("/");
          return;
        }

        if (incRes.ok) setIncidents(await incRes.json());
        if (profRes.ok) setProfile(await profRes.json());
      } catch {
        // ignore network errors
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [router]);

  async function handleLogout() {
    await apiFetch("/api/auth/logout", { method: "POST" });
    router.push("/");
  }

  function parseMastery(): Record<string, number> {
    try {
      return profile?.categoryMasteryJson
        ? JSON.parse(profile.categoryMasteryJson)
        : {};
    } catch {
      return {};
    }
  }

  if (loading) {
    return (
      <main className={styles.main}>
        <div className={styles.loadingContainer}>
          <div className="spinner" />
          <p>Loading arena...</p>
        </div>
      </main>
    );
  }

  const mastery = parseMastery();

  return (
    <main className={styles.main}>
      {/* Header */}
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <div className={styles.headerLeft}>
            <span className={styles.logo}>⚡ Incident<span className={styles.logoAccent}>X</span></span>
            <span className={styles.modeBadge}>Practice Mode</span>
          </div>
          <div className={styles.headerRight}>
            <button onClick={handleLogout} className="btn btn-ghost">
              Logout
            </button>
          </div>
        </div>
      </header>

      <div className={styles.layout}>
        {/* Sidebar stats */}
        <aside className={styles.sidebar}>
          <div className={`glass-card ${styles.statsCard}`}>
            <h3 className={styles.statsTitle}>Your Stats</h3>
            <div className={styles.statGrid}>
              <div className={styles.stat}>
                <span className={styles.statValue}>
                  {profile?.rating?.toFixed(0) || "1500"}
                </span>
                <span className={styles.statLabel}>Rating</span>
              </div>
              <div className={styles.stat}>
                <span className={styles.statValue}>
                  {profile?.solvedCount || 0}/{incidents.length}
                </span>
                <span className={styles.statLabel}>Solved</span>
              </div>
              <div className={styles.stat}>
                <span className={styles.statValue}>
                  {profile?.attemptsCount || 0}
                </span>
                <span className={styles.statLabel}>Attempts</span>
              </div>
              <div className={styles.stat}>
                <span className={styles.statValue}>
                  {profile && profile.attemptsCount > 0
                    ? (
                        (profile.solvedCount / profile.attemptsCount) *
                        100
                      ).toFixed(0) + "%"
                    : "—"}
                </span>
                <span className={styles.statLabel}>Pass Rate</span>
              </div>
            </div>
          </div>

          {/* Category mastery */}
          <div className={`glass-card ${styles.masteryCard}`}>
            <h3 className={styles.statsTitle}>Category Mastery</h3>
            {Object.keys(mastery).length > 0 ? (
              <div className={styles.masteryList}>
                {Object.entries(mastery).map(([cat, val]) => (
                  <div key={cat} className={styles.masteryItem}>
                    <div className={styles.masteryLabel}>
                      <span>{categoryIcons[cat] || "📦"}</span>
                      <span>{cat}</span>
                    </div>
                    <div className={styles.masteryBarTrack}>
                      <div
                        className={styles.masteryBarFill}
                        style={{ width: `${val}%` }}
                      />
                    </div>
                    <span className={styles.masteryValue}>{val}%</span>
                  </div>
                ))}
              </div>
            ) : (
              <p className={styles.emptyText}>
                Solve incidents to unlock mastery tracking.
              </p>
            )}
          </div>
        </aside>

        {/* Incidents grid */}
        <section className={styles.incidentsSection}>
          <h2 className={styles.sectionTitle}>
            Active Incidents
            <span className={styles.incidentCount}>{incidents.length}</span>
          </h2>

          <div className={`stagger ${styles.incidentGrid}`}>
            {incidents.map((inc) => (
              <button
                key={inc.id}
                className={`glass-card ${styles.incidentCard}`}
                onClick={() => router.push(`/incident/${inc.id}`)}
              >
                <div className={styles.incidentCardTop}>
                  <span className={styles.incidentIcon}>
                    {categoryIcons[inc.category] || "📦"}
                  </span>
                  <span
                    className={`badge badge-${inc.difficulty.toLowerCase()}`}
                  >
                    {inc.difficulty}
                  </span>
                </div>
                <h3 className={styles.incidentTitle}>{inc.title}</h3>
                <div className={styles.incidentMeta}>
                  <span className={styles.incidentCategory}>{inc.category}</span>
                  <span className={styles.incidentArrow}>→</span>
                </div>
              </button>
            ))}
          </div>
        </section>
      </div>
    </main>
  );
}
