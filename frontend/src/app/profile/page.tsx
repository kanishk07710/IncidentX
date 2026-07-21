"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { apiFetch } from "@/lib/api";
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

export default function ProfilePage() {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [profile, setProfile] = useState<PlayerProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const router = useRouter();

  useEffect(() => {
    async function load() {
      try {
        const [userRes, profRes] = await Promise.all([
          apiFetch("/api/auth/me"),
          apiFetch("/api/profiles/me"),
        ]);
        if (userRes.status === 401) {
          router.push("/");
          return;
        }
        if (userRes.ok) setUser(await userRes.json());
        if (profRes.ok) setProfile(await profRes.json());
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [router]);

  if (loading) {
    return (
      <main className={styles.loadingContainer}>
        <div className="spinner" />
        <p>Loading profile…</p>
      </main>
    );
  }

  const displayName = user?.name || user?.username || "Operator";
  const providerLabel = user?.authProvider
    ? PROVIDER_LABEL[user.authProvider.toUpperCase()] || user.authProvider
    : "Unknown";
  const joinDate = user?.createdAt
    ? new Date(user.createdAt).toLocaleDateString(undefined, {
        year: "numeric",
        month: "long",
        day: "numeric",
      })
    : null;

  return (
    <main className={styles.shell}>
      <div className={styles.topRow}>
        <Link href="/dashboard" className={styles.backLink}>
          ← Back to Dashboard
        </Link>
      </div>

      <div className={`glass-card ${styles.card}`}>
        <div className={styles.header}>
          {user?.avatarUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={user.avatarUrl} alt="" className={styles.avatar} />
          ) : (
            <div className={styles.avatarFallback}>{displayName.charAt(0).toUpperCase()}</div>
          )}
          <div>
            <h1 className={styles.name}>{displayName}</h1>
            <p className={styles.username}>@{user?.username}</p>
          </div>
        </div>

        <div className={styles.providerBadge}>
          <span>Signed in with {providerLabel}</span>
          <span className={styles.checkBadge}>✓</span>
        </div>

        <div className={styles.detailsGrid}>
          {user?.email && (
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
          {user?.githubId && (
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
