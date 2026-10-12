import http.server,ssl
from pathlib import Path
DATA=Path('desktop/ui/demo.mp4').read_bytes()
class Handler(http.server.BaseHTTPRequestHandler):
 def do_GET(self):
  self.send_response(200);self.send_header('Content-Type','video/mp4');self.send_header('Content-Length',str(len(DATA)))
  if self.path.startswith('/cors.mp4'):self.send_header('Access-Control-Allow-Origin','*')
  self.end_headers();self.wfile.write(DATA)
 def log_message(self,*args):pass
server=http.server.HTTPServer(('127.0.0.1',8443),Handler)
context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.load_cert_chain('artifacts/test-media.crt','artifacts/test-media.key');server.socket=context.wrap_socket(server.socket,server_side=True);server.serve_forever()
