import random
import json # 
from locust import HttpUser, task, between


VALID_KEYS = [
    "dev-key-token", "dev-key-fixed", "dev-key-sliding", 
    "dev-key-leaky", "dev-key-business", "key-acme-dashboard", 
    "key-acme-api", "key-beta-dashboard", "key-beta-api", 
    "key-enterprise-dashboard", "key-enterprise-api", "dev-key-dynamic"
]

class ApiGatewayUser(HttpUser):
    wait_time = between(1, 3)

    def on_start(self):
        # Pick one of the valid keys for this virtual user session
        self.api_key = random.choice(VALID_KEYS)
        self.guid = f"test-{random.randint(1000, 9999)}"

    def _get_headers(self):
        # Generate a fake IP on the fly for every request
        fake_ip = f"{random.randint(1, 255)}.{random.randint(1, 255)}.{random.randint(1, 255)}.{random.randint(1, 255)}"
        return {
            "X-API-KEY": self.api_key,
            "X-Forwarded-For": fake_ip,
            "Content-Type": "application/json"
        }

    @task(3)
    def get_notes(self):
        with self.client.get(f"/api/{self.guid}/notes", headers=self._get_headers(), catch_response=True) as response:
            if response.status_code in [200, 429, 403]:
                response.success()

    @task(1)
    def create_note(self):
        # 1. Create the raw Python dictionary
        payload_dict = {
            "id": str(random.randint(1000, 9999)),
            "content": "Hello from Locust"
        }
        
        # Pass the dictionary directly to the 'json=' parameter.
        with self.client.post(
            f"/api/{self.guid}/notes", 
            json=payload_dict, 
            headers=self._get_headers(), 
            catch_response=True
        ) as response:
            # Treat 429 (Rate Limit) and 403 (Abuse Block) as expected "successes"
            if response.status_code in [200, 201, 429, 403]:
                response.success()
            else:
                response.failure(f"Got {response.status_code}: {response.text}")