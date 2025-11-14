import fetch from 'node-fetch';

// Vercel serverless function to proxy /api/v1/* to the EC2 backend
export default async function handler(req, res) {
  const backendBaseUrl = process.env.BACKEND_BASE_URL;

  if (!backendBaseUrl) {
    return res.status(500).json({
      message: 'BACKEND_BASE_URL is not configured on the server.',
    });
  }

  // Remove the leading "/api/v1" from the path and forward the rest
  const subPath = req.url.replace(/^\/api\/v1/, '');
  const targetUrl = `${backendBaseUrl}/api/v1${subPath}`;

  const proxyHeaders = { ...req.headers };
  delete proxyHeaders.host;

  // Optional: shared secret header for backend auth hardening
  if (process.env.PROXY_SHARED_SECRET) {
    proxyHeaders['x-proxy-secret'] = process.env.PROXY_SHARED_SECRET;
  }

  try {
    const response = await fetch(targetUrl, {
      method: req.method,
      headers: proxyHeaders,
      body: ['GET', 'HEAD'].includes(req.method) ? undefined : req,
    });

    // Forward status and headers
    res.status(response.status);
    response.headers.forEach((value, key) => {
      if (key.toLowerCase() === 'transfer-encoding') return;
      res.setHeader(key, value);
    });

    const buffer = await response.arrayBuffer();
    res.send(Buffer.from(buffer));
  } catch (error) {
    console.error('Proxy error:', error);
    res.status(502).json({ message: 'Bad Gateway: error contacting backend.' });
  }
}
