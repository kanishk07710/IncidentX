"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import styles from "./settings.module.css";

const THEME_KEY = "incidentx_dashboard_theme";

export default function SettingsPage() {
  const [theme, setTheme] = useState<"light" | "dark">("light");

  useEffect(() => {
    function init() {
      const saved = window.localStorage.getItem(THEME_KEY);
      if (saved === "dark" || saved === "light") setTheme(saved);
    }
    init();
  }, []);

  return (
    <main className={styles.shell} data-theme={theme}>
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
