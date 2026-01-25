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


def test_post_replies_with_valid_request_returns_201():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = str(auth_response.user_id)

    original_content = f"Original post {uuid4().hex[:8]}"
    body = PostPostsBody(user_id=auth_response.user_id, content=original_content)
    create_response = post_posts.sync(client=client, body=body)
    assert create_response is not None
    post_id = str(create_response.post_id)

    reply_content = f"Reply content {uuid4().hex[:8]}"
    response = requests.post(
        f"{BASE_URL}/posts/{post_id}/replies",
        json={"userId": user_id, "content": reply_content},
    )

    assert response.status_code == 201
    data = response.json()
    assert UUID_PATTERN.match(data["replyPostId"])
    assert UUID_PATTERN.match(data["replyToPostId"])
    assert UUID_PATTERN.match(data["userId"])
    assert data["replyToPostId"] == post_id
    assert data["userId"] == user_id
    assert data["content"] == reply_content
    assert ISO8601_PATTERN.match(data["createdAt"])


def test_post_replies_with_nonexistent_post_returns_404():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = str(auth_response.user_id)

    reply_content = f"Reply content {uuid4().hex[:8]}"
    response = requests.post(
        f"{BASE_URL}/posts/00000000-0000-0000-0000-000000000000/replies",
        json={"userId": user_id, "content": reply_content},
    )

    assert response.status_code == 404
    data = response.json()
    assert data["error"] == "Post not found"


def test_post_replies_with_invalid_content_returns_400():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = str(auth_response.user_id)

    original_content = f"Original post {uuid4().hex[:8]}"
    body = PostPostsBody(user_id=auth_response.user_id, content=original_content)
    create_response = post_posts.sync(client=client, body=body)
    assert create_response is not None
    post_id = str(create_response.post_id)

    response = requests.post(
        f"{BASE_URL}/posts/{post_id}/replies",
        json={"userId": user_id, "content": ""},
    )

    assert response.status_code == 400
    data = response.json()
    assert data["error"] == "Content is invalid"


def test_post_replies_with_content_exceeding_280_graphemes_returns_400():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = str(auth_response.user_id)

    original_content = f"Original post {uuid4().hex[:8]}"
    body = PostPostsBody(user_id=auth_response.user_id, content=original_content)
    create_response = post_posts.sync(client=client, body=body)
    assert create_response is not None
    post_id = str(create_response.post_id)

    long_content = "a" * 281
    response = requests.post(
        f"{BASE_URL}/posts/{post_id}/replies",
        json={"userId": user_id, "content": long_content},
    )

    assert response.status_code == 400
    data = response.json()
    assert data["error"] == "Content is invalid"
