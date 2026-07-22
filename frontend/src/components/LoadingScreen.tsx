import styles from "./LoadingScreen.module.css";

interface LoadingScreenProps {
  title: string;
  waking?: boolean;
  wakingMessage?: string;
  theme?: "light" | "dark";
}

const DEFAULT_WAKING_MESSAGE =
  "Waking up the server — this can take up to a minute after a period of inactivity…";

export default function LoadingScreen({
  title,
  waking = false,
  wakingMessage = DEFAULT_WAKING_MESSAGE,
  theme = "dark",
}: LoadingScreenProps) {
  return (
    <main className={styles.screen} data-theme={theme}>
      <span className={styles.brand}>⚡ IncidentX</span>

      <div className={styles.sonar} aria-hidden="true">
        <span className={styles.sonarRing} />
        <span className={styles.sonarRing} />
        <span className={styles.sonarRing} />
        <span className={styles.sonarCore} />
      </div>

      <div className={styles.textBlock}>
        <p className={styles.title}>{waking ? "Waking up the server" : title}</p>
        {waking && <p className={styles.subtitle}>{wakingMessage}</p>}
      </div>

      <div className={styles.progressTrack}>
        <div className={styles.progressFill} />
      </div>
    </main>
  );
}
