"use client";
import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import type { CurrentUser } from "@/lib/types";
import styles from "./UserProfileMenu.module.css";

interface ProviderMeta {
  label: string;
  icon: React.ReactNode;
}

const GithubIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z" />
  </svg>
);

const GoogleIcon = () => (
  <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
    <path fill="#4285F4" d="M15.68 8.18c0-.57-.05-1.11-.14-1.64H8v3.1h4.3a3.68 3.68 0 0 1-1.6 2.42v2h2.59c1.52-1.4 2.4-3.46 2.4-5.88Z" />
    <path fill="#34A853" d="M8 16c2.16 0 3.97-.71 5.29-1.94l-2.59-2c-.72.48-1.63.77-2.7.77-2.08 0-3.84-1.4-4.47-3.29H.85v2.07A8 8 0 0 0 8 16Z" />
    <path fill="#FBBC05" d="M3.53 9.54A4.8 4.8 0 0 1 3.27 8c0-.53.09-1.05.26-1.54V4.4H.85a8 8 0 0 0 0 7.2l2.68-2.06Z" />
    <path fill="#EA4335" d="M8 3.18c1.17 0 2.23.4 3.06 1.19l2.3-2.3A7.96 7.96 0 0 0 8 0 8 8 0 0 0 .85 4.4l2.68 2.06C4.16 4.58 5.92 3.18 8 3.18Z" />
  </svg>
);

const LocalIcon = () => (
  <svg viewBox="0 0 16 16" width="14" height="14" fill="currentColor" aria-hidden="true">
    <path d="M8 8a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm0 1.5c-2.67 0-8 1.34-8 4V15h16v-1.5c0-2.66-5.33-4-8-4Z" />
  </svg>
);

const PROVIDER_META: Record<string, ProviderMeta> = {
  GITHUB: { label: "GitHub", icon: <GithubIcon /> },
  GOOGLE: { label: "Google", icon: <GoogleIcon /> },
  LOCAL: { label: "Local Account", icon: <LocalIcon /> },
};

function providerMeta(provider: string | undefined): ProviderMeta {
  if (!provider) return { label: "Unknown", icon: <LocalIcon /> };
  return (
    PROVIDER_META[provider.toUpperCase()] || {
      label: provider.charAt(0) + provider.slice(1).toLowerCase(),
      icon: <LocalIcon />,
    }
  );
}

function formatJoinDate(iso: string | undefined): string | null {
  if (!iso) return null;
  try {
    return new Date(iso).toLocaleDateString(undefined, {
      year: "numeric",
      month: "long",
      day: "numeric",
    });
  } catch {
    return null;
  }
}

export default function UserProfileMenu({
  user,
  onLogout,
}: {
  // Non-null by contract: the caller must resolve the authenticated user before rendering this
  // menu at all (e.g. behind a loading gate) rather than passing null and letting a fabricated
  // placeholder name stand in for a real one.
  user: CurrentUser;
  onLogout: () => void;
}) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    function handleEscape(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", handleOutside);
    document.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handleOutside);
      document.removeEventListener("keydown", handleEscape);
    };
  }, []);

  const username = user.username;
  const displayName = user.name || username;
  const provider = providerMeta(user.authProvider);
  const joinDate = formatJoinDate(user.createdAt);

  return (
    <div className={styles.container} ref={containerRef}>
      <button
        type="button"
        className={styles.trigger}
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="true"
        aria-expanded={open}
      >
        {user.avatarUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={user.avatarUrl} alt="" className={styles.avatarImg} />
        ) : (
          <div className={styles.avatarFallback}>{username.charAt(0).toUpperCase()}</div>
        )}
        <div className={styles.triggerText}>
          <div className={styles.profileName}>{displayName}</div>
          <div className={styles.profileTag}>Practice Mode</div>
        </div>
        <span className={`${styles.chevron} ${open ? styles.chevronOpen : ""}`}>⌄</span>
      </button>

      {open && (
        <div className={styles.dropdown} role="menu">
          <div className={styles.dropdownHeader}>
            {user.avatarUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={user.avatarUrl} alt="" className={styles.avatarImgLg} />
            ) : (
              <div className={styles.avatarFallbackLg}>{username.charAt(0).toUpperCase()}</div>
            )}
            <div className={styles.headerText}>
              <div className={styles.headerName}>{displayName}</div>
              <div className={styles.headerUsername}>@{username}</div>
            </div>
          </div>

          <div className={styles.providerBadge}>
            <span className={styles.providerIcon}>{provider.icon}</span>
            <span>Signed in with {provider.label}</span>
            <span className={styles.checkBadge}>✓</span>
          </div>

          {(user.email || joinDate) && (
            <div className={styles.detailsList}>
              {user.email && (
                <div className={styles.detailRow}>
                  <span className={styles.detailLabel}>Email</span>
                  <span className={styles.detailValue}>{user.email}</span>
                </div>
              )}
              {joinDate && (
                <div className={styles.detailRow}>
                  <span className={styles.detailLabel}>Joined</span>
                  <span className={styles.detailValue}>{joinDate}</span>
                </div>
              )}
            </div>
          )}

          <div className={styles.divider} />

          <Link href="/profile" className={styles.menuItem} role="menuitem" onClick={() => setOpen(false)}>
            View Profile
          </Link>
          <Link href="/settings" className={styles.menuItem} role="menuitem" onClick={() => setOpen(false)}>
            Settings
          </Link>
          <button
            type="button"
            className={`${styles.menuItem} ${styles.menuItemDanger}`}
            role="menuitem"
            onClick={() => {
              setOpen(false);
              onLogout();
            }}
          >
            Logout
          </button>
        </div>
      )}
    </div>
  );
}
