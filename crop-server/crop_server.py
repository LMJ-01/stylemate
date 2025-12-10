from flask import Flask, request, jsonify
from flask_cors import CORS
from rembg import remove
from PIL import Image
import io
import base64
import requests

app = Flask(__name__)
CORS(app)  # 🔥 localhost:8080 에서 호출할 수 있게 CORS 허용

@app.route("/crop", methods=["POST"])
def crop_image():
    """
    요청 JSON:
      { "imageUrl": "https://....jpg" }

    응답 JSON:
      { "success": true, "pngBase64": "....." }
    """
    try:
        data = request.get_json(silent=True) or {}
        url = data.get("imageUrl")

        if not url:
            return jsonify({"success": False, "error": "no imageUrl"}), 400

        # 상대경로(예: "/uploads/xxx.jpg")면 localhost:8080 기준으로 보정
        if not url.startswith("http"):
            url = "http://localhost:8080" + url

        # 1) 이미지 다운로드
        res = requests.get(url, timeout=10)

        res.raise_for_status()

        # 2) PIL 이미지로 로드
        img = Image.open(io.BytesIO(res.content)).convert("RGBA")

        # 3) rembg로 배경 제거
        output = remove(img)

        # 4) PNG → base64 인코딩
        buffer = io.BytesIO()
        output.save(buffer, format="PNG")
        encoded = base64.b64encode(buffer.getvalue()).decode("utf-8")

        return jsonify({
            "success": True,
            "pngBase64": encoded
        })

    except Exception as e:
        print("crop error:", e)
        return jsonify({"success": False, "error": str(e)}), 500


if __name__ == "__main__":
    # 포트 5001에서 실행
    app.run(host="0.0.0.0", port=5001)
