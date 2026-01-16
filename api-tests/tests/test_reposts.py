import re
from uuid import uuid4

import requests

from lib.api_config import BASE_URL
from openapi_gen.micro_chirp_api_client.api.auth import post_auth_login
from openapi_gen.micro_chirp_api_client.api.posts import post_posts
from openapi_gen.micro_chirp_api_client.client import Client
from openapi_gen.micro_chirp_api_client.models.post_posts_body import PostPostsBody

UUID_PATTERN = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
ISO8601_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})$")


def test_post_reposts_with_valid_request_returns_201():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = str(auth_response.user_id)

    expected_content = f"Test content {uuid4().hex[:8]}"
    body = PostPostsBody(user_id=auth_response.user_id, content=expected_content)
    create_response = post_posts.sync(client=client, body=body)
    assert create_response is not None
    post_id = str(create_response.post_id)

    response = requests.post(
        f"{BASE_URL}/posts/{post_id}/reposts",
        json={"userId": user_id},
    )

    assert response.status_code == 201
    data = response.json()
    assert UUID_PATTERN.match(data["postId"])
    assert UUID_PATTERN.match(data["userId"])
    assert data["postId"] == post_id
    assert data["userId"] == user_id
    assert ISO8601_PATTERN.match(data["repostedAt"])


def test_post_reposts_with_nonexistent_post_returns_404():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = str(auth_response.user_id)

    response = requests.post(
        f"{BASE_URL}/posts/00000000-0000-0000-0000-000000000000/reposts",
        json={"userId": user_id},
    )

    assert response.status_code == 404
    data = response.json()
    assert data["error"] == "Post not found"
