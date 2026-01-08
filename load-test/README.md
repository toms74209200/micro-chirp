# Load Testing

## Environments

- Python
- [uv](https://docs.astral.sh/uv/) 0.9.13

## Setup

```bash
uv sync
```

Generate OpenAPI client code (optional):

```bash
uv run openapi-python-client generate --path ../spec/openapi.yml --output-path openapi_gen --overwrite
```

## Usage

### Run with Web UI

```bash
uv run locust
```

Then open http://localhost:8089 in your browser.

### Run in Headless Mode

```bash
uv run locust --headless
```

### Run Specific Test Class

```bash
uv run locust LoginAPI
uv run locust CreatePostAPI
uv run locust GetPostAPI
```
