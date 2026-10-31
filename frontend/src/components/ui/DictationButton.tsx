"use client";

import { cn } from "@/lib/utils";
import { Mic, Square } from "lucide-react";
import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";

/**
 * CC#40: dictado por voz para los reportes de seguimiento. Usa el reconocimiento de voz del propio
 * navegador (Web Speech API, español de México): no guarda audio y solo agrega texto al campo; la
 * asesora lo revisa y lo guarda con el botón del formulario. Lo dictado se agrega al final del texto
 * existente, nunca lo sustituye.
 *
 * Privacidad: en Chrome y Edge el navegador procesa la voz con el servicio de su fabricante; en
 * Safari se procesa en el dispositivo o con Apple. Firefox no lo soporta (se muestra el aviso).
 */

type RecognitionResultEvent = {
  resultIndex: number;
  results: ArrayLike<{ isFinal: boolean; 0: { transcript: string } }>;
};
type RecognitionErrorEvent = { error: string };
type Recognition = {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((e: RecognitionResultEvent) => void) | null;
  onerror: ((e: RecognitionErrorEvent) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
};
type RecognitionCtor = new () => Recognition;

function recognitionCtor(): RecognitionCtor | null {
  if (typeof window === "undefined") return null;
  const w = window as unknown as { SpeechRecognition?: RecognitionCtor; webkitSpeechRecognition?: RecognitionCtor };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

const errorMessages: Record<string, string> = {
  "not-allowed": "No hay permiso para usar el micrófono. Actívalo en la configuración del navegador y vuelve a intentar.",
  "service-not-allowed": "El navegador no permite el dictado en esta página. Puedes escribir el reporte.",
  "no-speech": "No se detectó voz. Vuelve a intentarlo hablando cerca del micrófono.",
  "audio-capture": "No se encontró un micrófono disponible.",
  network: "Falló la conexión del servicio de dictado. Tu texto se conserva; vuelve a intentar.",
  aborted: "",
};

const noopSubscribe = () => () => {};

/** Une el texto existente con lo dictado sin pegar palabras. */
export function appendDictation(current: string, dictated: string): string {
  const piece = dictated.trim();
  if (!piece) return current;
  if (!current.trim()) return piece.charAt(0).toUpperCase() + piece.slice(1);
  const sep = /\s$/.test(current) ? "" : " ";
  return current + sep + piece;
}

export function DictationButton({
  value,
  onChange,
  className,
  label = "Dictar reporte",
}: {
  value: string;
  onChange: (next: string) => void;
  className?: string;
  label?: string;
}) {
  // null en el servidor; en el navegador, si tiene reconocimiento de voz.
  const supported = useSyncExternalStore(noopSubscribe, () => recognitionCtor() !== null, () => null);
  const [listening, setListening] = useState(false);
  const [interim, setInterim] = useState("");
  const [error, setError] = useState<string | null>(null);
  const recognitionRef = useRef<Recognition | null>(null);
  // El texto cambia mientras se dicta; se lee siempre el más reciente para no sobrescribir lo escrito.
  const valueRef = useRef(value);
  const onChangeRef = useRef(onChange);
  useEffect(() => {
    valueRef.current = value;
    onChangeRef.current = onChange;
  });

  // El micrófono no queda activo al salir de la captura (cerrar el modal o cambiar de pantalla).
  useEffect(() => () => recognitionRef.current?.abort(), []);

  const stop = useCallback(() => {
    recognitionRef.current?.stop();
  }, []);

  const start = useCallback(() => {
    const Ctor = recognitionCtor();
    if (!Ctor) return;
    setError(null);
    const recognition = new Ctor();
    recognition.lang = "es-MX";
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.onresult = (event) => {
      let pending = "";
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i];
        if (result.isFinal) {
          const next = appendDictation(valueRef.current, result[0].transcript);
          valueRef.current = next;
          onChangeRef.current(next);
        } else {
          pending += result[0].transcript;
        }
      }
      setInterim(pending);
    };
    recognition.onerror = (event) => {
      const message = errorMessages[event.error] ?? "No se pudo transcribir. Tu texto se conserva; vuelve a intentar o escríbelo.";
      if (message) setError(message);
    };
    recognition.onend = () => {
      setListening(false);
      setInterim("");
      recognitionRef.current = null;
    };
    recognitionRef.current = recognition;
    try {
      recognition.start();
      setListening(true);
    } catch {
      setError("No se pudo iniciar el dictado. Vuelve a intentar.");
    }
  }, []);

  if (supported === null) return null;

  return (
    <div className={cn("flex flex-col gap-1", className)}>
      {supported ? (
        <button
          type="button"
          onClick={listening ? stop : start}
          aria-pressed={listening}
          className={cn(
            "inline-flex w-fit items-center gap-1.5 rounded-lg border px-2.5 py-1 text-xs font-medium transition-colors",
            listening ? "border-danger-text bg-danger-bg text-danger-text" : "border-border bg-card text-obsessed hover:border-gold",
          )}
        >
          {listening ? (
            <>
              <span className="relative flex h-2 w-2">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-danger-text opacity-75" />
                <span className="relative inline-flex h-2 w-2 rounded-full bg-danger-text" />
              </span>
              <Square className="h-3 w-3" aria-hidden /> Escuchando… toca para detener
            </>
          ) : (
            <>
              <Mic className="h-3.5 w-3.5" aria-hidden /> {label}
            </>
          )}
        </button>
      ) : (
        <p className="text-xs text-muted">Este navegador no permite dictar por voz; usa Chrome, Edge o Safari, o escribe el reporte.</p>
      )}
      {listening && interim ? <p className="text-xs italic text-muted">{interim}</p> : null}
      {listening ? <p className="text-xs text-muted">Lo dictado se agrega al final del texto. Revísalo antes de guardar.</p> : null}
      {error ? (
        <p role="alert" className="text-xs text-danger-text">
          {error}
        </p>
      ) : null}
    </div>
  );
}
