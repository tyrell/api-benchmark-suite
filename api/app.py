from flask import Flask, jsonify, request
import jwt
import datetime
import uuid
from functools import wraps
import logging

app = Flask(__name__)

# OAuth Configuration
SECRET_KEY = "your-secret-key-change-in-production"
ALGORITHM = "HS256"

# In-memory store for demo purposes (use a proper database in production)
clients = {
    "demo-client-id": {
        "client_secret": "demo-client-secret",
        "scopes": ["api:read", "api:write", "admin"]
    },
    "test-client": {
        "client_secret": "test-secret",
        "scopes": ["api:read"]
    }
}

# Sample data for testing
customers = []
cev_events = []

# Logging setup
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def require_auth(required_scopes=None):
    """Decorator to require OAuth token authentication"""
    def decorator(f):
        @wraps(f)
        def decorated_function(*args, **kwargs):
            auth_header = request.headers.get('Authorization')
            
            if not auth_header or not auth_header.startswith('Bearer '):
                return jsonify({"error": "Missing or invalid authorization header"}), 401
            
            token = auth_header.split(' ')[1]
            
            try:
                payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
                
                # Check if token is expired
                if payload.get('exp', 0) < datetime.datetime.utcnow().timestamp():
                    return jsonify({"error": "Token expired"}), 401
                
                # Check scopes if required
                if required_scopes:
                    token_scopes = payload.get('scopes', [])
                    if not any(scope in token_scopes for scope in required_scopes):
                        return jsonify({"error": "Insufficient permissions"}), 403
                
                # Add token info to request context
                request.token_info = payload
                
            except jwt.InvalidTokenError:
                return jsonify({"error": "Invalid token"}), 401
            
            return f(*args, **kwargs)
        return decorated_function
    return decorator

# OAuth Token Endpoint
@app.route('/oauth/token', methods=['POST'])
def get_token():
    """OAuth2 Client Credentials Grant"""
    
    # Get client credentials
    client_id = request.form.get('client_id') or request.json.get('client_id') if request.json else None
    client_secret = request.form.get('client_secret') or request.json.get('client_secret') if request.json else None
    grant_type = request.form.get('grant_type') or request.json.get('grant_type') if request.json else None
    scope = request.form.get('scope', '') or request.json.get('scope', '') if request.json else ''
    
    logger.info(f"Token request: client_id={client_id}, grant_type={grant_type}, scope={scope}")
    
    # Validate grant type
    if grant_type != 'client_credentials':
        return jsonify({
            "error": "unsupported_grant_type",
            "error_description": "Only client_credentials grant type is supported"
        }), 400
    
    # Validate client credentials
    if not client_id or not client_secret:
        return jsonify({
            "error": "invalid_request",
            "error_description": "client_id and client_secret are required"
        }), 400
    
    client = clients.get(client_id)
    if not client or client['client_secret'] != client_secret:
        return jsonify({
            "error": "invalid_client",
            "error_description": "Invalid client credentials"
        }), 401
    
    # Validate requested scopes
    requested_scopes = scope.split() if scope else []
    allowed_scopes = client['scopes']
    
    # Filter to only allowed scopes
    granted_scopes = [s for s in requested_scopes if s in allowed_scopes]
    if not granted_scopes and requested_scopes:
        granted_scopes = ['api:read']  # Default scope
    elif not granted_scopes:
        granted_scopes = allowed_scopes  # Grant all allowed scopes
    
    # Generate JWT token
    now = datetime.datetime.utcnow()
    payload = {
        'iss': 'api-benchmark-suite',  # issuer
        'sub': client_id,              # subject (client)
        'aud': 'api',                  # audience
        'iat': int(now.timestamp()),   # issued at
        'exp': int((now + datetime.timedelta(hours=1)).timestamp()),  # expires in 1 hour
        'scopes': granted_scopes,
        'client_id': client_id,
        'jti': str(uuid.uuid4())       # JWT ID
    }
    
    token = jwt.encode(payload, SECRET_KEY, algorithm=ALGORITHM)
    
    logger.info(f"Token granted: client_id={client_id}, scopes={granted_scopes}")
    
    return jsonify({
        "access_token": token,
        "token_type": "Bearer",
        "expires_in": 3600,
        "scope": " ".join(granted_scopes)
    }), 200

# Health Check Endpoint (no auth required)
@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({
        "status": "healthy", 
        "timestamp": datetime.datetime.utcnow().isoformat(),
        "service": "api-benchmark-suite"
    }), 200

# Original hello endpoint (no auth required for backward compatibility)
@app.route('/api/hello', methods=['GET'])
def hello():
    return jsonify({"message": "Hello, world!"}), 200

# Protected Hello Endpoint
@app.route('/api/hello/protected', methods=['GET'])
@require_auth(['api:read'])
def protected_hello():
    client_id = request.token_info.get('client_id', 'unknown')
    scopes = request.token_info.get('scopes', [])
    return jsonify({
        "message": f"Hello, authenticated client: {client_id}!",
        "your_scopes": scopes,
        "timestamp": datetime.datetime.utcnow().isoformat()
    }), 200

# Customer Management API
@app.route('/api/customers', methods=['GET'])
@require_auth(['api:read'])
def get_customers():
    return jsonify({"customers": customers, "count": len(customers)}), 200

@app.route('/api/customers', methods=['POST'])
@require_auth(['api:write'])
def create_customer():
    data = request.get_json()
    if not data or 'name' not in data:
        return jsonify({"error": "Customer name is required"}), 400
    
    customer = {
        "id": str(uuid.uuid4()),
        "name": data['name'],
        "email": data.get('email', ''),
        "created_at": datetime.datetime.utcnow().isoformat()
    }
    customers.append(customer)
    
    logger.info(f"Customer created: {customer['id']} by {request.token_info.get('client_id')}")
    return jsonify(customer), 201

@app.route('/api/customers/<customer_id>', methods=['GET'])
@require_auth(['api:read'])
def get_customer(customer_id):
    customer = next((c for c in customers if c['id'] == customer_id), None)
    if not customer:
        return jsonify({"error": "Customer not found"}), 404
    return jsonify(customer), 200

@app.route('/api/customers/<customer_id>', methods=['PUT'])
@require_auth(['api:write'])
def update_customer(customer_id):
    customer = next((c for c in customers if c['id'] == customer_id), None)
    if not customer:
        return jsonify({"error": "Customer not found"}), 404
    
    data = request.get_json()
    if not data:
        return jsonify({"error": "Request body required"}), 400
    
    customer.update({
        'name': data.get('name', customer['name']),
        'email': data.get('email', customer['email']),
        'updated_at': datetime.datetime.utcnow().isoformat()
    })
    
    logger.info(f"Customer updated: {customer_id} by {request.token_info.get('client_id')}")
    return jsonify(customer), 200

@app.route('/api/customers/<customer_id>', methods=['DELETE'])
@require_auth(['api:write'])
def delete_customer(customer_id):
    global customers
    customer = next((c for c in customers if c['id'] == customer_id), None)
    if not customer:
        return jsonify({"error": "Customer not found"}), 404
    
    customers = [c for c in customers if c['id'] != customer_id]
    logger.info(f"Customer deleted: {customer_id} by {request.token_info.get('client_id')}")
    return jsonify({"message": "Customer deleted"}), 200

# CEV Events API
@app.route('/api/cev-events', methods=['GET'])
@require_auth(['api:read'])
def get_cev_events():
    return jsonify({"events": cev_events, "count": len(cev_events)}), 200

@app.route('/api/cev-events', methods=['POST'])
@require_auth(['api:write'])
def create_cev_event():
    data = request.get_json()
    if not data or 'type' not in data or 'data' not in data:
        return jsonify({"error": "Event type and data are required"}), 400
    
    event = {
        "id": str(uuid.uuid4()),
        "type": data['type'],
        "data": data['data'],
        "timestamp": datetime.datetime.utcnow().isoformat(),
        "created_by": request.token_info.get('client_id', 'unknown')
    }
    cev_events.append(event)
    
    logger.info(f"CEV event created: {event['id']} by {request.token_info.get('client_id')}")
    return jsonify(event), 201

@app.route('/api/cev-events/<event_id>', methods=['GET'])
@require_auth(['api:read'])
def get_cev_event(event_id):
    event = next((e for e in cev_events if e['id'] == event_id), None)
    if not event:
        return jsonify({"error": "Event not found"}), 404
    return jsonify(event), 200

@app.route('/api/cev-events/<event_id>', methods=['PUT'])
@require_auth(['api:write'])
def update_cev_event(event_id):
    event = next((e for e in cev_events if e['id'] == event_id), None)
    if not event:
        return jsonify({"error": "Event not found"}), 404
    
    data = request.get_json()
    if not data:
        return jsonify({"error": "Request body required"}), 400
    
    event.update({
        'type': data.get('type', event['type']),
        'data': data.get('data', event['data']),
        'updated_at': datetime.datetime.utcnow().isoformat(),
        'updated_by': request.token_info.get('client_id', 'unknown')
    })
    
    logger.info(f"CEV event updated: {event_id} by {request.token_info.get('client_id')}")
    return jsonify(event), 200

# Admin endpoint (requires admin scope)
@app.route('/api/admin/stats', methods=['GET'])
@require_auth(['admin'])
def admin_stats():
    return jsonify({
        "total_customers": len(customers),
        "total_events": len(cev_events),
        "clients_configured": len(clients),
        "server_time": datetime.datetime.utcnow().isoformat(),
        "uptime": "Runtime stats would go here"
    }), 200

# Token info endpoint (for debugging)
@app.route('/api/token/info', methods=['GET'])
@require_auth(['api:read'])
def token_info():
    return jsonify(request.token_info), 200

if __name__ == '__main__':
    print("Starting API Benchmark Suite Test Server...")
    print("Available OAuth clients:")
    for client_id, client_info in clients.items():
        print(f"  - Client ID: {client_id}")
        print(f"    Secret: {client_info['client_secret']}")
        print(f"    Scopes: {', '.join(client_info['scopes'])}")
    print("\nEndpoints:")
    print("  OAuth Token: POST /oauth/token")
    print("  Health Check: GET /api/health")
    print("  Hello (public): GET /api/hello")
    print("  Hello (protected): GET /api/hello/protected")
    print("  Customers: GET/POST /api/customers")
    print("  Customer: GET/PUT/DELETE /api/customers/<id>")
    print("  CEV Events: GET/POST /api/cev-events")
    print("  CEV Event: GET/PUT /api/cev-events/<id>")
    print("  Admin Stats: GET /api/admin/stats")
    print("  Token Info: GET /api/token/info")
    print(f"\nServer starting on http://0.0.0.0:5050")
    app.run(host='0.0.0.0', port=5050, debug=False)
