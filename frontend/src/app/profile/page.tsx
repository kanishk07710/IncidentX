"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { apiFetchWithRetry } from "@/lib/api";
import type { CurrentUser } from "@/lib/types";
import styles from "./profile.module.css";

interface PlayerProfile {
  rating: number;
  attemptsCount: number;
  solvedCount: number;
}

const PROVIDER_LABEL: Record<string, string> = {
  GITHUB: "GitHub",
  GOOGLE: "Google",
  LOCAL: "Local Account",
};

const THEME_KEY = "incidentx_dashboard_theme";

export default function ProfilePage() {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [profile, setProfile] = useState<PlayerProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [theme, setTheme] = useState<"light" | "dark">("light");
  const router = useRouter();

  useEffect(() => {
    function init() {
      const saved = window.localStorage.getItem(THEME_KEY);
      if (saved === "dark" || saved === "light") setTheme(saved);
    }
    init();
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setLoadError(false);
      try {
        const [userRes, profRes] = await Promise.all([
          apiFetchWithRetry("/api/auth/me"),
          apiFetchWithRetry("/api/profiles/me"),
        ]);
        if (cancelled) return;

        if (userRes.status === 401) {
          router.push("/");
          return;
        }

        // All-or-nothing: a lone failed request used to be swallowed silently, leaving stale
        // null state rendered as if it were final instead of retried or surfaced as an error.
        if (!userRes.ok || !profRes.ok) {
          setLoadError(true);
          return;
        }

        const [userData, profileData] = await Promise.all([userRes.json(), profRes.json()]);
        if (cancelled) return;

        setUser(userData);
        setProfile(profileData);
      } catch {
        if (!cancelled) setLoadError(true);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [router, reloadKey]);

  if (loadError) {
    return (
      <main className={styles.loadingContainer} data-theme={theme}>
        <p>Couldn&rsquo;t load your profile.</p>
        <button className="btn btn-primary" onClick={() => setReloadKey((k) => k + 1)}>
          Try Again
        </button>
      </main>
    );
  }

  if (loading || !user) {
    // The `!user` half is a type-narrowing safety net, not expected in practice: load() above
    // only clears `loading` after both the user and profile requests have succeeded together.
    return (
      <main className={styles.loadingContainer} data-theme={theme}>
        <div className="spinner" />
        <p>Loading profile…</p>
      </main>
    );
  }

  const displayName = user.name || user.username;
  const providerLabel = PROVIDER_LABEL[user.authProvider.toUpperCase()] || user.authProvider;
  const joinDate = user.createdAt
    ? new Date(user.createdAt).toLocaleDateString(undefined, {
        year: "numeric",
        month: "long",
        day: "numeric",
      })
    : null;

  return (
    <main className={styles.shell} data-theme={theme}>
      <div className={styles.topRow}>
        <Link href="/dashboard" className={styles.backLink}>
          ← Back to Dashboard
        </Link>
      </div>

      <div className={`glass-card ${styles.card}`}>
        <div className={styles.header}>
          {user.avatarUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={user.avatarUrl} alt="" className={styles.avatar} />
          ) : (
            <div className={styles.avatarFallback}>{displayName.charAt(0).toUpperCase()}</div>
          )}
          <div>
            <h1 className={styles.name}>{displayName}</h1>
            <p className={styles.username}>@{user.username}</p>
          </div>
        </div>

        <div className={styles.providerBadge}>
          <span>Signed in with {providerLabel}</span>
          <span className={styles.checkBadge}>✓</span>
        </div>

        <div className={styles.detailsGrid}>
          {user.email && (
            <div className={styles.detailItem}>
              <span className={styles.detailLabel}>Email</span>
              <span className={styles.detailValue}>{user.email}</span>
            </div>
          )}
          {joinDate && (
            <div className={styles.detailItem}>
              <span className={styles.detailLabel}>Member Since</span>
              <span className={styles.detailValue}>{joinDate}</span>
            </div>
          )}
          {user.githubId && (
            <div className={styles.detailItem}>
              <span className={styles.detailLabel}>GitHub ID</span>
              <span className={styles.detailValue}>{user.githubId}</span>
            </div>
          )}
        </div>

        {profile && (
          <div className={styles.statsRow}>
            <div className={styles.statBox}>
              <span className={styles.statValue}>{Math.round(profile.rating)}</span>
              <span className={styles.statLabel}>Rating</span>
            </div>
            <div className={styles.statBox}>
              <span className={styles.statValue}>{profile.solvedCount}</span>
              <span className={styles.statLabel}>Solved</span>
            </div>
            <div className={styles.statBox}>
              <span className={styles.statValue}>{profile.attemptsCount}</span>
              <span className={styles.statLabel}>Attempts</span>
            </div>
          </div>
        )}
      </div>
    </main>
  );
}
