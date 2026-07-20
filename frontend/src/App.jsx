import { useMemo, useState } from "react";
import {
  Check,
  Clipboard,
  ExternalLink,
  Link2,
  Loader2,
  RefreshCw,
  X
} from "lucide-react";

const HISTORY_KEY = "short-link-history";
const shortLinkBase =
  import.meta.env.VITE_SHORTLINK_BASE || "http://localhost:8080";
const apiOrigin =
  import.meta.env.VITE_API_ORIGIN || (import.meta.env.PROD ? shortLinkBase : "");

const sampleUrls = [
  "https://spring.io/projects/spring-boot",
  "https://vite.dev/guide/",
  "https://react.dev/learn"
];

function readHistory() {
  try {
    return JSON.parse(localStorage.getItem(HISTORY_KEY)) || [];
  } catch {
    return [];
  }
}

function normalizeOrigin(origin) {
  return origin.endsWith("/") ? origin.slice(0, -1) : origin;
}

function buildShortUrl(shortCode) {
  return `${normalizeOrigin(shortLinkBase)}/${shortCode}`;
}

function buildApiUrl(path) {
  if (!apiOrigin) {
    return path;
  }

  return `${normalizeOrigin(apiOrigin)}${path}`;
}

function isHttpUrl(value) {
  try {
    const parsed = new URL(value);
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch {
    return false;
  }
}

function getFriendlyError(message) {
  if (!message) {
    return "生成失败，请稍后再试。";
  }

  if (message.includes("URL cannot be null") || message.includes("Invalid URL")) {
    return "请输入完整的 http 或 https 链接。";
  }

  return message;
}

function getRequestError(error) {
  if (error instanceof TypeError) {
    return "请求后端失败，请检查 VITE_API_ORIGIN 和后端 CORS 配置。";
  }

  return error.message || "生成失败，请确认后端服务已启动。";
}

export default function App() {
  const [original, setOriginal] = useState("");
  const [result, setResult] = useState(null);
  const [history, setHistory] = useState(readHistory);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");
  const [copiedCode, setCopiedCode] = useState("");

  const trimmedUrl = original.trim();
  const canSubmit = trimmedUrl.length > 0 && !isLoading;
  const previewHost = useMemo(() => {
    if (!isHttpUrl(trimmedUrl)) {
      return "请输入完整链接";
    }

    return new URL(trimmedUrl).host;
  }, [trimmedUrl]);

  async function createShortLink(event) {
    event.preventDefault();
    setError("");
    setResult(null);

    if (!isHttpUrl(trimmedUrl)) {
      setError("链接需要以 http:// 或 https:// 开头。");
      return;
    }

    setIsLoading(true);

    try {
      const response = await fetch(buildApiUrl("/api/link"), {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ original: trimmedUrl })
      });

      const payload = await response.json().catch(() => ({}));

      if (!response.ok) {
        throw new Error(getFriendlyError(payload.message));
      }

      const nextResult = {
        shortCode: payload.shortCode,
        originalUrl: payload.originalUrl,
        shortUrl: buildShortUrl(payload.shortCode),
        createdAt: new Date().toISOString()
      };

      const nextHistory = [
        nextResult,
        ...history.filter((item) => item.shortCode !== nextResult.shortCode)
      ].slice(0, 5);

      setResult(nextResult);
      setHistory(nextHistory);
      localStorage.setItem(HISTORY_KEY, JSON.stringify(nextHistory));
    } catch (err) {
      setError(getRequestError(err));
    } finally {
      setIsLoading(false);
    }
  }

  async function copyText(value, code) {
    try {
      await navigator.clipboard.writeText(value);
      setCopiedCode(code);
      window.setTimeout(() => setCopiedCode(""), 1600);
    } catch {
      setError("复制失败，可以手动选中文本复制。");
    }
  }

  function clearForm() {
    setOriginal("");
    setResult(null);
    setError("");
  }

  function fillSample() {
    const sample = sampleUrls[Math.floor(Math.random() * sampleUrls.length)];
    setOriginal(sample);
    setError("");
  }

  return (
    <main className="app-shell">
      <section className="hero">
        <div className="hero-copy">
          <span className="eyebrow">Short Link</span>
          <h1>短链接生成器</h1>
          <p>输入一个原始链接，生成可访问的短链接。默认有效期 30 天。</p>
        </div>

        <div className="service-card" aria-label="接口信息">
          <div>
            <span>创建接口</span>
            <strong>POST /api/link</strong>
          </div>
          <div>
            <span>访问格式</span>
            <strong>/{`{shortCode}`}</strong>
          </div>
          <div>
            <span>有效期</span>
            <strong>30 天</strong>
          </div>
        </div>
      </section>

      <section className="workspace" aria-label="短链接工作台">
        <form className="creator-panel" onSubmit={createShortLink}>
          <div className="panel-heading">
            <div>
              <span className="section-kicker">新建</span>
              <h2>生成短链接</h2>
            </div>
            <button
              className="icon-button"
              type="button"
              onClick={clearForm}
              aria-label="清空输入"
              title="清空输入"
            >
              <RefreshCw size={18} aria-hidden="true" />
            </button>
          </div>

          <label className="url-field">
            <span>原始链接</span>
            <div className="input-wrap">
              <Link2 size={20} aria-hidden="true" />
              <input
                value={original}
                onChange={(event) => setOriginal(event.target.value)}
                placeholder="https://example.com/a/very/long/path"
                autoComplete="url"
                spellCheck="false"
              />
              {original && (
                <button
                  className="ghost-icon"
                  type="button"
                  onClick={() => setOriginal("")}
                  aria-label="清除链接"
                  title="清除链接"
                >
                  <X size={18} aria-hidden="true" />
                </button>
              )}
            </div>
          </label>

          <div className="form-meta">
            <span>{previewHost}</span>
            <button className="sample-button" type="button" onClick={fillSample}>
              换个示例
            </button>
          </div>

          {error && <div className="message error-message">{error}</div>}

          <button className="submit-button" type="submit" disabled={!canSubmit}>
            {isLoading ? (
              <Loader2 className="spin" size={20} aria-hidden="true" />
            ) : (
              <Link2 size={20} aria-hidden="true" />
            )}
            <span>{isLoading ? "正在生成" : "生成短链接"}</span>
          </button>
        </form>

        <aside className="result-panel" aria-label="生成结果">
          <div className="panel-heading compact">
            <div>
              <span className="section-kicker">结果</span>
              <h2>短链接</h2>
            </div>
          </div>

          {result ? (
            <ResultCard
              item={result}
              copiedCode={copiedCode}
              onCopy={copyText}
            />
          ) : (
            <div className="empty-state">
              <div className="empty-icon">
                <Link2 size={28} aria-hidden="true" />
              </div>
              <p>生成后的短链接会显示在这里。</p>
            </div>
          )}
        </aside>
      </section>

      <section className="history-section" aria-label="最近生成">
        <div className="history-heading">
          <div>
            <span className="section-kicker">历史</span>
            <h2>最近生成</h2>
          </div>
          {history.length > 0 && (
            <span className="history-count">{history.length}/5</span>
          )}
        </div>

        {history.length > 0 ? (
          <div className="history-grid">
            {history.map((item) => (
              <ResultCard
                key={`${item.shortCode}-${item.createdAt}`}
                item={item}
                copiedCode={copiedCode}
                onCopy={copyText}
                isSmall
              />
            ))}
          </div>
        ) : (
          <p className="history-empty">暂无记录。本页只保留最近 5 条本机记录。</p>
        )}
      </section>
    </main>
  );
}

function ResultCard({ item, copiedCode, onCopy, isSmall = false }) {
  const isCopied = copiedCode === item.shortCode;

  return (
    <article className={isSmall ? "result-card small" : "result-card"}>
      <div className="code-row">
        <span className="code-pill">{item.shortCode}</span>
        <span className="valid-pill">30 天</span>
      </div>

      <a className="short-url" href={item.shortUrl} target="_blank" rel="noreferrer">
        {item.shortUrl}
      </a>

      <p className="original-url">{item.originalUrl}</p>

      <div className="card-actions">
        <button
          className="action-button"
          type="button"
          onClick={() => onCopy(item.shortUrl, item.shortCode)}
        >
          {isCopied ? (
            <Check size={18} aria-hidden="true" />
          ) : (
            <Clipboard size={18} aria-hidden="true" />
          )}
          <span>{isCopied ? "已复制" : "复制"}</span>
        </button>
        <a className="icon-button link-button" href={item.shortUrl} target="_blank" rel="noreferrer" title="打开短链接">
          <ExternalLink size={18} aria-hidden="true" />
        </a>
      </div>
    </article>
  );
}
