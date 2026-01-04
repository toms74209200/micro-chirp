import re

import requests

from lib.api_config import BASE_URL
from openapi_gen.micro_chirp_api_client.api.auth import post_auth_login
from openapi_gen.micro_chirp_api_client.client import Client

UUID_PATTERN = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")


def test_post_posts_with_valid_request_returns_201():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = str(auth_response.user_id)

    response = requests.post(
        f"{BASE_URL}/posts",
        json={"userId": user_id, "content": "Hello, world!"},
    )

    assert response.status_code == 201
    data = response.json()
    assert UUID_PATTERN.match(data["postId"])
    assert data["userId"] == user_id
    assert data["content"] == "Hello, world!"
    assert data["createdAt"] is not None


def test_post_posts_with_blank_content_returns_400():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = str(auth_response.user_id)

    # Try to create post with blank content
    response = requests.post(
        f"{BASE_URL}/posts",
        json={"userId": user_id, "content": "   "},
    )

    assert response.status_code == 400
    data = response.json()
    assert data["error"] == "Content is invalid"


def test_post_posts_with_content_exceeding_280_graphemes_returns_400():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = str(auth_response.user_id)

    response = requests.post(
        f"{BASE_URL}/posts",
        json={"userId": user_id, "content": "a" * 281},
    )

    assert response.status_code == 400
    data = response.json()
    assert data["error"] == "Content is invalid"


def test_post_posts_with_nonexistent_user_id_returns_400():
    response = requests.post(
        f"{BASE_URL}/posts",
        json={"userId": "00000000-0000-0000-0000-000000000000", "content": "Hello, world!"},
    )

    assert response.status_code == 400
    data = response.json()
    assert data["error"] == "User not found"
