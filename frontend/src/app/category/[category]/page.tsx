"use client";
import { useEffect, useMemo, useState, use } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { apiFetch } from "@/lib/api";
import { categoryMeta, tierFor } from "@/lib/categories";
import styles from "./category.module.css";

interface Incident {
  id: string;
  title: string;
  difficulty: string;
  category: string;
}

interface UserProfile {
  categoryMasteryJson: string;
  solvedIncidentsJson: string;
}

export default function CategoryPage({
  params,
}: {
  params: Promise<{ category: string }>;
}) {
  const { category } = use(params);
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

  const categoryIncidents = useMemo(
    () => incidents.filter((i) => i.category === category),
    [incidents, category]
  );
  const solvedCount = categoryIncidents.filter((i) => solvedIds.includes(i.id)).length;
  const meta = categoryMeta(category);
  const val = mastery[category] || 0;
  const tier = tierFor(val);

  if (loading) {
    return (
      <main className={styles.loadingContainer}>
        <div className="spinner" />
        <p>Loading category...</p>
      </main>
    );
  }

  return (
    <div className={styles.shell}>
      <header className={styles.topbar}>
        <div className={styles.topbarInner}>
          <Link href="/dashboard" className={styles.backLink}>
            ← Dashboard
          </Link>
          <span className={styles.brand}>⚡ IncidentX</span>
        </div>
      </header>

      <main className={styles.main}>
        <div className={styles.header}>
          <div className={styles.iconWrap}>{meta.icon}</div>
          <div className={styles.headerInfo}>
            <h1 className={styles.title}>{meta.label}</h1>
            <p className={styles.desc}>{meta.desc}</p>
            <div className={styles.headerStats}>
              <div className={styles.headerStat}>
                <span className={styles.headerStatLabel}>Solved</span>
                <span className={styles.headerStatValue}>
                  {solvedCount}/{categoryIncidents.length}
                </span>
              </div>
              <div className={styles.headerStat}>
                <span className={styles.headerStatLabel}>Tier</span>
                <span className={styles.headerStatValue}>{tier.label}</span>
              </div>
              <div className={styles.masteryTrackWrap}>
                <span className={styles.headerStatLabel}>Mastery — {val}%</span>
                <div className={styles.masteryTrack}>
                  <div className={styles.masteryFill} style={{ width: `${val}%` }} />
                </div>
              </div>
            </div>
          </div>
        </div>

        <h2 className={styles.sectionTitle}>
          {categoryIncidents.length} Incident{categoryIncidents.length === 1 ? "" : "s"}
        </h2>

        {categoryIncidents.length === 0 ? (
          <div className={styles.emptyState}>No incidents in this category yet.</div>
        ) : (
          <div className={styles.list}>
            {categoryIncidents.map((inc) => {
              const solved = solvedIds.includes(inc.id);
              const diffClass =
                inc.difficulty === "EASY"
                  ? styles.diffEasy
                  : inc.difficulty === "MEDIUM"
                  ? styles.diffMedium
                  : styles.diffHard;
              return (
                <Link key={inc.id} href={`/incident/${inc.id}`} className={styles.card}>
                  <span className={`${styles.solvedMark} ${solved ? styles.solvedMarkDone : ""}`}>
                    {solved ? "✓" : ""}
                  </span>
                  <div className={styles.cardInfo}>
                    <div className={styles.cardTitle}>{inc.title}</div>
                    <div className={styles.cardMeta}>{solved ? "Solved" : "Not attempted yet"}</div>
                  </div>
                  <span className={`${styles.difficulty} ${diffClass}`}>{inc.difficulty}</span>
                  <span className={styles.cardArrow}>→</span>
                </Link>
              );
            })}
          </div>
        )}
      </main>
    </div>
  );
}
