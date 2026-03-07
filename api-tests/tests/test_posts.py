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


def test_post_posts_with_valid_request_returns_201():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = str(auth_response.user_id)

    expected_content = f"Test content {uuid4().hex[:8]}"
    response = requests.post(
        f"{BASE_URL}/posts",
        json={"userId": user_id, "content": expected_content},
    )

    assert response.status_code == 201
    data = response.json()
    assert UUID_PATTERN.match(data["postId"])
    assert data["userId"] == user_id
    assert data["content"] == expected_content
    assert ISO8601_PATTERN.match(data["createdAt"])


def test_post_posts_with_blank_content_returns_400():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = str(auth_response.user_id)

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
    expected_content = f"Test content {uuid4().hex[:8]}"
    response = requests.post(
        f"{BASE_URL}/posts",
        json={"userId": "00000000-0000-0000-0000-000000000000", "content": expected_content},
    )

    assert response.status_code == 400
    data = response.json()
    assert data["error"] == "User not found"


def test_get_posts_by_id_with_existing_post_returns_200():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = auth_response.user_id

    expected_content = f"Test content {uuid4().hex[:8]}"
    body = PostPostsBody(user_id=user_id, content=expected_content)
    create_response = post_posts.sync(client=client, body=body)
    assert create_response is not None
    post_id = str(create_response.post_id)

    response = requests.get(f"{BASE_URL}/posts/{post_id}")

    assert response.status_code == 200
    data = response.json()
    assert data["postId"] == post_id
    assert data["userId"] == str(user_id)
    assert data["content"] == expected_content
    assert ISO8601_PATTERN.match(data["createdAt"])
    assert data["likeCount"] == 0
    assert data["repostCount"] == 0
    assert data["replyCount"] == 0
    assert data["viewCount"] == 0
    assert data["isLikedByCurrentUser"] is None
    assert data["isRepostedByCurrentUser"] is None


def test_get_posts_by_id_with_nonexistent_post_returns_404():
    response = requests.get(f"{BASE_URL}/posts/00000000-0000-0000-0000-000000000000")

    assert response.status_code == 404
    data = response.json()
    assert data["error"] == "Post not found"


def test_get_posts_by_id_with_user_id_parameter_returns_200():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = auth_response.user_id

    expected_content = f"Test content {uuid4().hex[:8]}"
    body = PostPostsBody(user_id=user_id, content=expected_content)
    create_response = post_posts.sync(client=client, body=body)
    assert create_response is not None
    post_id = str(create_response.post_id)

    response = requests.get(f"{BASE_URL}/posts/{post_id}", params={"userId": str(user_id)})

    assert response.status_code == 200
    data = response.json()
    assert data["postId"] == post_id
    assert data["userId"] == str(user_id)
    assert data["content"] == expected_content
    assert data["isLikedByCurrentUser"] is False
    assert data["isRepostedByCurrentUser"] is False


def test_get_posts_by_id_with_deleted_post_returns_404():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = auth_response.user_id

    expected_content = f"Test content {uuid4().hex[:8]}"
    body = PostPostsBody(user_id=user_id, content=expected_content)
    create_response = post_posts.sync(client=client, body=body)
    assert create_response is not None
    post_id = str(create_response.post_id)

    delete_response = requests.delete(
        f"{BASE_URL}/posts/{post_id}",
        json={"userId": str(user_id)},
    )
    assert delete_response.status_code == 204

    response = requests.get(f"{BASE_URL}/posts/{post_id}")

    assert response.status_code == 404
    data = response.json()
    assert data["error"] == "Post not found"


def test_delete_posts_by_id_with_valid_request_returns_204():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = auth_response.user_id

    expected_content = f"Test content {uuid4().hex[:8]}"
    body = PostPostsBody(user_id=user_id, content=expected_content)
    create_response = post_posts.sync(client=client, body=body)
    assert create_response is not None
    post_id = str(create_response.post_id)

    response = requests.delete(
        f"{BASE_URL}/posts/{post_id}",
        json={"userId": str(user_id)},
    )

    assert response.status_code == 204


def test_delete_posts_by_id_with_nonexistent_post_returns_404():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = auth_response.user_id

    response = requests.delete(
        f"{BASE_URL}/posts/00000000-0000-0000-0000-000000000000",
        json={"userId": str(user_id)},
    )

    assert response.status_code == 404
    data = response.json()
    assert data["error"] == "Post not found"


def test_delete_posts_by_id_with_different_user_returns_403():
    client = Client(base_url=BASE_URL)
    auth_response1 = post_auth_login.sync(client=client)
    user_id1 = auth_response1.user_id

    expected_content = f"Test content {uuid4().hex[:8]}"
    body = PostPostsBody(user_id=user_id1, content=expected_content)
    create_response = post_posts.sync(client=client, body=body)
    assert create_response is not None
    post_id = str(create_response.post_id)

    auth_response2 = post_auth_login.sync(client=client)
    user_id2 = auth_response2.user_id

    response = requests.delete(
        f"{BASE_URL}/posts/{post_id}",
        json={"userId": str(user_id2)},
    )

    assert response.status_code == 403
    data = response.json()
    assert data["error"] == "User is not the post author"


def test_get_post_with_liked_post_returns_like_info():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = str(auth_response.user_id)

    expected_content = f"Test content {uuid4().hex[:8]}"
    body = PostPostsBody(user_id=auth_response.user_id, content=expected_content)
    create_response = post_posts.sync(client=client, body=body)
    assert create_response is not None
    post_id = str(create_response.post_id)

    like_response = requests.post(
        f"{BASE_URL}/posts/{post_id}/likes",
        json={"userId": user_id},
    )
    assert like_response.status_code == 201

    response = requests.get(f"{BASE_URL}/posts/{post_id}", params={"userId": user_id})

    assert response.status_code == 200
    data = response.json()
    assert data["likeCount"] == 1
    assert data["isLikedByCurrentUser"] is True


def test_get_posts_with_empty_ids_returns_200_with_empty_posts():
    response = requests.get(f"{BASE_URL}/posts", params={"ids": ""})

    assert response.status_code == 200
    data = response.json()
    assert data["posts"] == []
    assert data["total"] == 0


def test_get_posts_with_ids_returns_200():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = auth_response.user_id

    body1 = PostPostsBody(user_id=user_id, content=f"Post A {uuid4().hex[:8]}")
    body2 = PostPostsBody(user_id=user_id, content=f"Post B {uuid4().hex[:8]}")
    post1 = post_posts.sync(client=client, body=body1)
    post2 = post_posts.sync(client=client, body=body2)
    assert post1 is not None and post2 is not None
    post_id1 = str(post1.post_id)
    post_id2 = str(post2.post_id)

    response = requests.get(f"{BASE_URL}/posts", params={"ids": f"{post_id1},{post_id2}"})

    assert response.status_code == 200
    data = response.json()
    assert data["total"] == 2
    assert data["limit"] == 20
    assert data["offset"] == 0
    returned_ids = [p["postId"] for p in data["posts"]]
    assert post_id1 in returned_ids
    assert post_id2 in returned_ids
    for post in data["posts"]:
        assert UUID_PATTERN.match(post["postId"])
        assert ISO8601_PATTERN.match(post["createdAt"])
        assert post["likeCount"] == 0
        assert post["isLikedByCurrentUser"] is None


def test_get_posts_with_user_id_returns_like_status():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = str(auth_response.user_id)

    body = PostPostsBody(user_id=auth_response.user_id, content=f"Post {uuid4().hex[:8]}")
    created = post_posts.sync(client=client, body=body)
    assert created is not None
    post_id = str(created.post_id)

    response = requests.get(f"{BASE_URL}/posts", params={"ids": post_id, "userId": user_id})

    assert response.status_code == 200
    data = response.json()
    assert len(data["posts"]) == 1
    assert data["posts"][0]["isLikedByCurrentUser"] is False
    assert data["posts"][0]["isRepostedByCurrentUser"] is False


def test_get_posts_with_liked_post_and_user_id_returns_is_liked_true():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = str(auth_response.user_id)

    body = PostPostsBody(user_id=auth_response.user_id, content=f"Post {uuid4().hex[:8]}")
    created = post_posts.sync(client=client, body=body)
    assert created is not None
    post_id = str(created.post_id)

    like_response = requests.post(
        f"{BASE_URL}/posts/{post_id}/likes",
        json={"userId": user_id},
    )
    assert like_response.status_code == 201

    response = requests.get(
        f"{BASE_URL}/posts",
        params={"ids": post_id, "userId": user_id},
    )

    assert response.status_code == 200
    data = response.json()
    assert len(data["posts"]) == 1
    assert data["posts"][0]["likeCount"] == 1
    assert data["posts"][0]["isLikedByCurrentUser"] is True


def test_get_posts_with_limit_and_offset_returns_paginated_results():
    client = Client(base_url=BASE_URL)
    auth_response = post_auth_login.sync(client=client)
    user_id = auth_response.user_id

    posts = [
        post_posts.sync(client=client, body=PostPostsBody(user_id=user_id, content=f"Post {i} {uuid4().hex[:8]}"))
        for i in range(5)
    ]
    assert all(p is not None for p in posts)
    all_ids = ",".join(str(p.post_id) for p in posts)

    response = requests.get(
        f"{BASE_URL}/posts",
        params={"ids": all_ids, "limit": 2, "offset": 1},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["total"] == 5
    assert len(data["posts"]) == 2
    assert data["limit"] == 2
    assert data["offset"] == 1
