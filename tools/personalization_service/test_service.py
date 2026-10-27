import base64
import io
import json
import unittest
import uuid
import http.client
import threading
import os
from unittest.mock import patch
from PIL import Image
import service


def png(alpha=True):
    out = io.BytesIO()
    image = Image.new("RGBA", (512, 512), (0, 0, 0, 0 if alpha else 255))
    image.paste((60, 80, 100, 255), (128, 128, 384, 384))
    image.save(out, "PNG")
    return out.getvalue()


class Tests(unittest.TestCase):
    def setUp(self):
        self.meta = {"requestId": str(uuid.uuid4()), "itemId": "tired", "meaning": "Utrujena sem."}

    def multipart(self, metadata=None, duplicate=False):
        boundary = "novarehab_test"
        parts = [("metadata", json.dumps(metadata or self.meta).encode()), ("reference", png()), ("system", png())]
        if duplicate:
            parts.append(("reference", png()))
        data = b""
        for name, body in parts:
            data += f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n'.encode() + body + b"\r\n"
        return f"multipart/form-data; boundary={boundary}", data + f"--{boundary}--\r\n".encode()

    def test_two_references(self):
        metadata, reference, system = service.parts(*self.multipart())
        self.assertEqual(metadata, self.meta)
        self.assertEqual(Image.open(io.BytesIO(reference)).size, (512, 512))
        self.assertEqual(Image.open(io.BytesIO(system)).format, "PNG")

    def test_duplicate_rejected(self):
        with self.assertRaises(ValueError):
            service.parts(*self.multipart(duplicate=True))

    def test_path_item_rejected(self):
        self.meta["itemId"] = "../../secret"
        with self.assertRaises(ValueError):
            service.parts(*self.multipart())

    def test_invalid_reference_rejected(self):
        with self.assertRaises(Exception):
            service.pixels(b"not an image")

    def test_prompt_uses_system_and_identity(self):
        value = service.prompt(self.meta)
        self.assertIn("Image 1 is the identity", value)
        self.assertIn("Image 2 is the exact system icon", value)
        self.assertIn("Replace ONLY", value)
        self.assertIn("Patient LEFT", value)

    def response(self, data, code=200):
        class Response:
            status_code = code
            content = b"{}"
            def json(self):
                return {"data": [{"b64_json": base64.b64encode(data).decode()}]}
        return Response()

    def test_generation_two_attachments_and_transparency(self):
        def post(url, **kwargs):
            self.assertEqual(url, "https://api.openai.com/v1/images/edits")
            self.assertEqual(len(kwargs["files"]), 2)
            self.assertEqual(kwargs["data"]["background"], "transparent")
            self.assertFalse(kwargs["allow_redirects"])
            self.assertEqual(kwargs["headers"]["Authorization"], "Bearer test-key")
            return self.response(png())
        result = service.generate(self.meta, png(), png(), "test-key", post)
        with Image.open(io.BytesIO(result)) as image:
            self.assertEqual(image.size, (512, 512))
            self.assertEqual(image.getchannel("A").getextrema(), (0, 255))

    def test_opaque_rejected(self):
        with self.assertRaises(ValueError):
            service.generate(self.meta, png(), png(), "test", lambda *a, **k: self.response(png(False)))

    def test_provider_error(self):
        with self.assertRaises(RuntimeError):
            service.generate(self.meta, png(), png(), "test", lambda *a, **k: self.response(png(), 403))

    def test_exif_removed(self):
        with Image.open(io.BytesIO(service.pixels(png()))) as image:
            self.assertFalse(image.getexif())

    def http(self, token, path="/personalize", length=None):
        kind, body = self.multipart()
        server = service.ThreadingHTTPServer(("127.0.0.1", 0), service.Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=5)
            connection.request("POST", path, body, {"Authorization": "Bearer " + token,
                "Content-Type": kind, "Content-Length": str(length if length is not None else len(body))})
            response = connection.getresponse()
            result = response.status, dict(response.getheaders()), response.read()
            connection.close()
            return result
        finally:
            server.shutdown(); server.server_close(); thread.join()

    def test_http_authentication(self):
        with patch.dict(os.environ, {"PERSONALIZATION_ACCESS_TOKEN": "test-access"}):
            self.assertEqual(self.http("wrong")[0], 401)

    def test_http_no_provider_config(self):
        with patch.dict(os.environ, {"PERSONALIZATION_ACCESS_TOKEN": "test-access", "OPENAI_API_KEY": ""}):
            self.assertEqual(self.http("test-access")[0], 503)

    def test_http_scoped_response(self):
        with patch.dict(os.environ, {"PERSONALIZATION_ACCESS_TOKEN": "test-access", "OPENAI_API_KEY": "fake"}), patch.object(service, "generate", return_value=png()):
            status, headers, result = self.http("test-access")
            self.assertEqual(status, 200)
            self.assertEqual(headers["X-Request-Id"], self.meta["requestId"])
            self.assertEqual(headers["X-Item-Id"], self.meta["itemId"])
            self.assertEqual(headers["Cache-Control"], "no-store")
            self.assertEqual(result, png())

    def test_http_wrong_path(self):
        with patch.dict(os.environ, {"PERSONALIZATION_ACCESS_TOKEN": "test-access"}):
            self.assertEqual(self.http("test-access", "/other")[0], 404)

    def test_http_oversize(self):
        with patch.dict(os.environ, {"PERSONALIZATION_ACCESS_TOKEN": "test-access"}):
            self.assertEqual(self.http("test-access", length=service.MAX_UPLOAD + 1)[0], 413)


if __name__ == "__main__":
    unittest.main()
