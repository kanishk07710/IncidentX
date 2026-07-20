import { Client, type IMessage } from "@stomp/stompjs";
import { API_BASE } from "./api";

export interface SubmissionUpdate {
  id: number;
  status: string;
  results: string;
  aiFeedback: string;
}

function wsUrl(): string {
  return API_BASE.replace(/^http/, "ws") + "/ws";
}

// Live grading updates: connects a STOMP-over-WebSocket client, subscribes to one submission's
// topic, and calls onUpdate every time the backend publishes a new state for it (PENDING ->
// PASSED/FAILED/TIMEOUT/ERROR). Returns a cleanup function that tears the connection down —
// callers should invoke it once a terminal status arrives or the component unmounts.
export function subscribeToSubmission(
  submissionId: number,
  onUpdate: (update: SubmissionUpdate) => void
): () => void {
  const client = new Client({
    brokerURL: wsUrl(),
    reconnectDelay: 4000,
  });

  client.onConnect = () => {
    client.subscribe(`/topic/submissions/${submissionId}`, (message: IMessage) => {
      onUpdate(JSON.parse(message.body) as SubmissionUpdate);
    });
  };

  client.activate();

  return () => {
    client.deactivate();
  };
}
