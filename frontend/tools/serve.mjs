import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
const ROOT = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'))
http.createServer((req, res) => {
  const f = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]) === '/' ? 'devices.html' : req.url.split('?')[0])
  fs.readFile(f, (e, d) => {
    if (e) { res.writeHead(404); return res.end('nope') }
    res.writeHead(200, { 'content-type': f.endsWith('.html') ? 'text/html; charset=utf-8' : 'text/plain' })
    res.end(d)
  })
}).listen(8090, () => console.log('http://localhost:8090'))
