/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Build a fully static site (frontend/out) so Netlify can host it directly.
  output: 'export',
  images: { unoptimized: true },
};

module.exports = nextConfig;
