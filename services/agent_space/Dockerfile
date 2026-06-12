FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=UTC

RUN apt-get update && apt-get install -y --no-install-recommends \
    bash coreutils grep sed gawk jq less tree file curl wget dnsutils \
    zip unzip tar gzip bzip2 xz-utils p7zip-full git make cmake build-essential pkg-config \
    fonts-dejavu fonts-liberation fontconfig ca-certificates gnupg locales \
    python3 python3-pip python3-venv python3-dev python3-setuptools python3-wheel \
    ffmpeg imagemagick ghostscript tesseract-ocr tesseract-ocr-rus tesseract-ocr-eng \
    sqlite3 \
    && rm -rf /var/lib/apt/lists/*

RUN locale-gen en_US.UTF-8
ENV LANG=en_US.UTF-8
ENV LC_ALL=en_US.UTF-8

RUN update-alternatives --install /usr/bin/python python /usr/bin/python3 1
RUN pip3 install --no-cache-dir --upgrade pip setuptools wheel

RUN pip3 install --no-cache-dir \
    numpy pandas scipy scikit-learn matplotlib seaborn plotly openpyxl xlrd xlwt pyarrow polars duckdb statsmodels \
    pypdf2 pdfplumber reportlab weasyprint python-docx markdown mistune pytesseract Pillow \
    requests httpx aiohttp beautifulsoup4 lxml html5lib python-dotenv pyyaml toml jinja2 python-dateutil \
    click typer rich cryptography patool regex

RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && npm install -g typescript ts-node prettier eslint \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
RUN mkdir -p /workspace/tmp /workspace/input /workspace/output /workspace/scripts \
    && useradd --create-home --shell /bin/bash agent \
    && chown -R agent:agent /workspace /home/agent

COPY runner.py /usr/local/bin/agent_space_runner.py
RUN chmod 0755 /usr/local/bin/agent_space_runner.py

ENV AGENT_SPACE_HOST=0.0.0.0
ENV AGENT_SPACE_PORT=8080
ENV AGENT_SPACE_TMP_ROOT=/workspace/tmp

USER agent
EXPOSE 8080
ENTRYPOINT ["python3", "/usr/local/bin/agent_space_runner.py"]
