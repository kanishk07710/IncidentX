"use client";
import Link from "next/link";
import styles from "./settings.module.css";

export default function SettingsPage() {
  return (
    <main className={styles.shell}>
      <div className={styles.topRow}>
        <Link href="/dashboard" className={styles.backLink}>
          ← Back to Dashboard
        </Link>
      </div>

      <div className={`glass-card ${styles.card}`}>
        <span className={styles.icon}>⚙️</span>
        <h1 className={styles.title}>Settings</h1>
        <p className={styles.subtitle}>
          Account settings are coming soon. For now, manage your session from the profile menu.
        </p>
      </div>
    </main>
  );
}
