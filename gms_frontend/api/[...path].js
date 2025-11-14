// Vercel serverless function to proxy /api/* to the EC2 backend
// Example: /api/v1/auth/login -> http://EC2_HOST:8080/api/v1/auth/login

export default async function handler(req, res) {
  const backendBaseUrl = process.env.BACKEND_BASE_URL;

  if (!backendBaseUrl) {
    return res.status(500).json({
      message: 'BACKEND_BASE_URL is not configured on the server.',
    });
  }

  // Remove the leading "/api" from the path and forward the rest
  const subPath = req.url.replace(/^\/api/, '');
  const targetUrl = `${backendBaseUrl}${subPath}`; // backend already exposes /api/v1/... paths

  const proxyHeaders = { ...req.headers };
  delete proxyHeaders.host;

  if (process.env.PROXY_SHARED_SECRET) {
    proxyHeaders['x-proxy-secret'] = process.env.PROXY_SHARED_SECRET;
  }

  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 25000); // 25s timeout

    const response = await fetch(targetUrl, {
      method: req.method,
      headers: proxyHeaders,
      body: ['GET', 'HEAD'].includes(req.method) ? undefined : req,
      signal: controller.signal,
    });

    clearTimeout(timeout);

    res.status(response.status);
    for (const [key, value] of response.headers.entries()) {
      if (key.toLowerCase() === 'transfer-encoding') continue;
      res.setHeader(key, value);
    }

    const buffer = Buffer.from(await response.arrayBuffer());
    res.send(buffer);
  } catch (error) {
    console.error('Proxy error:', error);

    if (error.name === 'AbortError') {
      return res.status(504).json({ message: 'Gateway Timeout: backend did not respond in time.' });
    }

    res.status(502).json({ message: 'Bad Gateway: error contacting backend.' });
  }
}
