"use client";
import { useEffect, useMemo, useState, use } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { apiFetchWithRetry } from "@/lib/api";
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
  const [loadError, setLoadError] = useState(false);
  const [wakingUp, setWakingUp] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const router = useRouter();

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setLoadError(false);
      const onRetry = () => setWakingUp(true);
      try {
        const [incRes, profRes] = await Promise.all([
          apiFetchWithRetry("/api/incidents", {}, onRetry),
          apiFetchWithRetry("/api/profiles/me", {}, onRetry),
        ]);
        if (cancelled) return;

        if (incRes.status === 401 || profRes.status === 401) {
          router.push("/");
          return;
        }

        // All-or-nothing: a lone failed request used to be swallowed silently, which is exactly
        // what showed "0 incidents" for a real category during a brief post-deploy backend blip
        // instead of retrying or surfacing the failure.
        if (!incRes.ok || !profRes.ok) {
          setLoadError(true);
          return;
        }

        const [incidentsData, profileData] = await Promise.all([incRes.json(), profRes.json()]);
        if (cancelled) return;

        setIncidents(incidentsData);
        setProfile(profileData);
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
        <p>
          {wakingUp
            ? "Waking up the server — this can take up to a minute after a period of inactivity…"
            : "Loading category..."}
        </p>
      </main>
    );
  }

  if (loadError) {
    return (
      <main className={styles.loadingContainer}>
        <p>Couldn&rsquo;t load this category.</p>
        <button className="btn btn-primary" onClick={() => setReloadKey((k) => k + 1)}>
          Try Again
        </button>
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
