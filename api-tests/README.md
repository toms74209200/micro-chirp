# micro-chirp API Tests

## Environments

- Python
- [uv](https://docs.astral.sh/uv/) 0.9.13

## Setup

```bash
uv sync
```

Generate client code from OpenAPI spec (optional).

```bash
uv run openapi-python-client generate --path ../spec/openapi.yml --output-path openapi_gen --overwrite
```

## Usage

```bash
uv run pytest tests/ -vv
```

## Environment Variables

- `BASE_URL`: API server base URL (default: `http://localhost:8080`)

Example:

```bash
BASE_URL=http://localhost:8080 uv run pytest tests/ -vv
```
