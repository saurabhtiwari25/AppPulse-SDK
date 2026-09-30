"""
AppPulse Mock Analytics Backend

A simple Flask server that accepts analytics events from the AppPulse SDK.
Designed for development and demo purposes only.

Usage:
    python server.py

The server runs on http://0.0.0.0:8080 by default.
For the Android emulator, use http://10.0.2.2:8080 as the endpoint.

Features:
    - POST /events       — Accept a single event
    - POST /events/batch — Accept a batch of events
    - GET  /health       — Health check
    - GET  /events       — View all received events (debug)
    - DELETE /events     — Clear all received events (debug)

The server validates the X-API-Key header.
Valid key: "demo-api-key-12345"
"""

import json
import random
from datetime import datetime
from flask import Flask, request, jsonify

app = Flask(__name__)

# In-memory event store
received_events = []

# Valid API keys
VALID_API_KEYS = {"demo-api-key-12345"}

# Configurable failure simulation
SIMULATE_FAILURE_RATE = 0.0  # Set to 0.1 for 10% random failures


def validate_api_key():
    """Check the X-API-Key header."""
    api_key = request.headers.get("X-API-Key")
    if not api_key or api_key not in VALID_API_KEYS:
        return jsonify({"success": False, "message": "Invalid API key"}), 401
    return None


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return jsonify({
        "status": "ok",
        "timestamp": datetime.utcnow().isoformat(),
        "eventsReceived": len(received_events)
    })


@app.route("/events", methods=["POST"])
def post_event():
    """Accept a single analytics event."""
    # Validate API key
    auth_error = validate_api_key()
    if auth_error:
        return auth_error

    # Simulate random failures if configured
    if SIMULATE_FAILURE_RATE > 0 and random.random() < SIMULATE_FAILURE_RATE:
        return jsonify({"success": False, "message": "Simulated server error"}), 500

    # Parse event
    event = request.get_json()
    if not event:
        return jsonify({"success": False, "message": "Invalid JSON body"}), 400

    if not event.get("eventName"):
        return jsonify({"success": False, "message": "Missing eventName"}), 400

    # Store event
    event["_receivedAt"] = datetime.utcnow().isoformat()
    received_events.append(event)

    print(f"  [EVENT] {event.get('eventName')} "
          f"(user={event.get('userId', 'none')}, "
          f"id={event.get('eventId', '?')[:8]}...)")

    return jsonify({"success": True, "message": "Event received"}), 201


@app.route("/events/batch", methods=["POST"])
def post_events_batch():
    """Accept a batch of analytics events."""
    # Validate API key
    auth_error = validate_api_key()
    if auth_error:
        return auth_error

    # Simulate random failures if configured
    if SIMULATE_FAILURE_RATE > 0 and random.random() < SIMULATE_FAILURE_RATE:
        return jsonify({"success": False, "message": "Simulated server error"}), 500

    # Parse batch
    body = request.get_json()
    if not body or "events" not in body:
        return jsonify({"success": False, "message": "Invalid batch format"}), 400

    events = body["events"]
    if not isinstance(events, list):
        return jsonify({"success": False, "message": "'events' must be an array"}), 400

    # Store all events
    now = datetime.utcnow().isoformat()
    for event in events:
        event["_receivedAt"] = now
        received_events.append(event)

    print(f"  [BATCH] Received {len(events)} events")

    return jsonify({
        "success": True,
        "message": f"{len(events)} events received"
    }), 201


@app.route("/events", methods=["GET"])
def get_events():
    """Debug endpoint — view all received events."""
    return jsonify({
        "count": len(received_events),
        "events": received_events[-50:]  # Last 50 events
    })


@app.route("/events", methods=["DELETE"])
def delete_events():
    """Debug endpoint — clear all received events."""
    received_events.clear()
    return jsonify({"success": True, "message": "All events cleared"})


if __name__ == "__main__":
    print("=" * 50)
    print("  AppPulse Mock Analytics Backend")
    print("=" * 50)
    print(f"  Listening on http://0.0.0.0:8080")
    print(f"  Valid API key: demo-api-key-12345")
    print(f"  Failure simulation: {SIMULATE_FAILURE_RATE * 100}%")
    print()
    print("  For Android emulator, use: http://10.0.2.2:8080/")
    print("  For physical device, use your machine's LAN IP")
    print("=" * 50)
    print()

    app.run(host="0.0.0.0", port=8080, debug=True)
