FROM python:3.10-slim

WORKDIR /app

# Install system dependencies (if needed for psycopg2 or others)
RUN apt-get update && apt-get install -y \
    gcc \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Run the FastAPI server (which will also start the Discord bot)
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
