from locust import HttpUser, task, between

from lib.utils import random_string
from openapi_gen.micro_chirp_api_client.api.auth import post_auth_login
from openapi_gen.micro_chirp_api_client.api.posts import post_posts
from openapi_gen.micro_chirp_api_client.client import Client
from openapi_gen.micro_chirp_api_client.models.post_posts_body import PostPostsBody


class CreatePostAPI(HttpUser):
    """Test POST /posts API"""

    wait_time = between(1, 5)

    def on_start(self):
        client = Client(base_url=self.host)
        auth_response = post_auth_login.sync(client=client)
        self.user_id = str(auth_response.user_id)

    @task
    def create_post(self):
        self.client.post(
            "/posts",
            json={
                "userId": self.user_id,
                "content": f"Load test post {random_string(20)}",
            },
        )


class GetPostAPI(HttpUser):
    """Test GET /posts/{postId} API"""

    wait_time = between(1, 5)

    def on_start(self):
        client = Client(base_url=self.host)
        auth_response = post_auth_login.sync(client=client)
        self.user_id = auth_response.user_id

        body = PostPostsBody(user_id=self.user_id, content=f"Test post for GET {random_string(10)}")
        post_response = post_posts.sync(client=client, body=body)
        self.post_id = str(post_response.post_id)

    @task
    def get_post(self):
        self.client.get(f"/posts/{self.post_id}")
