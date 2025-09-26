"""
HYPOTHETICAL Customer API Test Stub Server

⚠️  WARNING: This is a HYPOTHETICAL/FICTIONAL API implementation for testing purposes only.
⚠️  This does NOT represent any real-world Customer API or actual business systems.
⚠️  All data, endpoints, and business logic are COMPLETELY FICTIONAL.

This Flask application implements a comprehensive test stub for a hypothetical Customer API v3.0.0.
It provides realistic responses for performance testing and development purposes ONLY.

🔒 SECURITY NOTICE: This is NOT a production system and contains NO real customer data.

Features (All Hypothetical):
- Fictional Customer API v3.0.0 endpoint implementation
- OAuth 2.0 authentication with configurable scopes (test purposes only)
- JSON API format compliance (application/vnd.api+json)
- Realistic FAKE test data generation
- Comprehensive error handling with proper HTTP status codes
- Fictional Individual and Organization customer support
- Mock vulnerability management
- Simulated lifecycle operations

Test Endpoints (All Fictional):
- GET /v3/brands/{brand}/customers - Search fake customers
- POST /v3/brands/{brand}/customers - Create mock customer
- GET /v3/brands/{brand}/customers/{id} - Get fictional customer
- PATCH /v3/brands/{brand}/customers/{id} - Update test customer
- GET /v3/brands/{brand}/customers/{id}/vulnerabilities - Get mock vulnerabilities
- PATCH /v3/brands/{brand}/customers/{id}/vulnerabilities/{vulnerabilityId} - Update fictional vulnerability
- POST /v3/brands/{brand}/customers/lifecycle - Mock lifecycle operations
- GET /api/health - Health check (OAuth not required)
- POST /oauth/token - Test OAuth token endpoint

🎭 DISCLAIMER: All customer data, brand names, and business logic are COMPLETELY FABRICATED.
"""

from flask import Flask, jsonify, request, g
from functools import wraps
import datetime
import jwt
import uuid
import logging
import random
import re

app = Flask(__name__)

# Configuration
SECRET_KEY = "your-secret-key-change-in-production"
ALGORITHM = "HS256"

# Logging setup
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# OAuth Client Configuration
OAUTH_CLIENTS = {
    "demo-client-id": {
        "client_secret": "demo-client-secret",
        "scopes": ["customer:read", "customer:write", "vulnerability:read", "vulnerability:write"]
    },
    "performance-test-client": {
        "client_secret": "test-secret-123",
        "scopes": ["customer:read", "customer:write", "vulnerability:read", "vulnerability:write"]
    }
}

# In-memory data stores (use proper database in production)
customers_db = {}
vulnerabilities_db = {}
lifecycle_events = []

def require_auth(required_scopes=None):
    """Decorator to require OAuth token authentication"""
    def decorator(f):
        @wraps(f)
        def decorated_function(*args, **kwargs):
            auth_header = request.headers.get('Authorization')
            
            if not auth_header or not auth_header.startswith('Bearer '):
                return create_error_response(401, "API-401", "Unauthorized", "Missing or invalid authorization header")
            
            token = auth_header.split(' ')[1]
            
            try:
                payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
                
                # Check scopes if required
                if required_scopes:
                    token_scopes = payload.get('scopes', [])
                    if not any(scope in token_scopes for scope in required_scopes):
                        return create_error_response(403, "API-403", "Forbidden", "Insufficient permissions")
                
                # Add token info to request context
                request.token_info = payload
                
            except jwt.ExpiredSignatureError:
                return create_error_response(401, "API-401", "Token Expired", "JWT token has expired")
            except jwt.InvalidTokenError:
                return create_error_response(401, "API-401", "Invalid Token", "JWT token is invalid")
            
            return f(*args, **kwargs)
        return decorated_function
    return decorator

def create_error_response(status_code, error_code, title, details, pointer=None):
    """Create standardized error response"""
    error = {
        "status": str(status_code),
        "code": error_code,
        "title": title,
        "details": details
    }
    
    if pointer:
        error["source"] = {"pointer": pointer}
    
    return jsonify({"errors": [error]}), status_code

def generate_customer_id():
    """Generate a realistic customer ID"""
    return str(random.randint(100000000, 999999999))

def generate_sample_individual():
    """Generate sample individual customer data"""
    first_names = ["John", "Jane", "Michael", "Sarah", "David", "Emily", "Robert", "Lisa", "James", "Maria"]
    last_names = ["Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Wilson", "Moore"]
    
    return {
        "firstName": random.choice(first_names),
        "middleName": random.choice(["James", "Marie", "Ann", "Lee", "Ray", ""]),
        "lastName": random.choice(last_names),
        "gender": random.choice(["MALE", "FEMALE"]),
        "deceased": False,
        "dateOfBirth": f"{random.randint(1950, 2000)}-{random.randint(1, 12):02d}-{random.randint(1, 28):02d}"
    }

def generate_sample_organization():
    """Generate sample organization customer data"""
    org_names = ["Tech Solutions Pty Ltd", "Global Services Corp", "Innovation Partners", "Digital Systems Ltd", "Business Solutions Group"]
    
    return {
        "registeredName": random.choice(org_names),
        "organisationType": random.choice(["Corporate", "ForProfit", "NotForProfit"]),
        "registrations": [
            {
                "registrationType": "ABN",
                "registeredNumber": str(random.randint(10000000000, 99999999999))
            }
        ]
    }

def generate_sample_address():
    """Generate sample postal address"""
    streets = ["Main St", "High St", "Park Ave", "Oak Rd", "First St", "Second Ave"]
    suburbs = ["Richmond", "Melbourne", "Sydney", "Brisbane", "Perth", "Adelaide"]
    states = ["VIC", "NSW", "QLD", "WA", "SA", "TAS"]
    
    return {
        "unitNumber": str(random.randint(1, 50)) if random.choice([True, False]) else None,
        "streetNumber": str(random.randint(1, 999)),
        "streetName": random.choice(streets),
        "locality": random.choice(suburbs),
        "state": random.choice(states),
        "postcode": str(random.randint(1000, 9999)),
        "countryCode": "AU",
        "addressType": "POSTAL"
    }

def generate_sample_customer(customer_type="individual", brand="AAMI"):
    """Generate a complete sample customer"""
    customer_id = generate_customer_id()
    
    customer_data = {
        "id": customer_id,
        "type": customer_type.capitalize(),
        "attributes": {
            "partyDetails": {
                "postalContact": generate_sample_address(),
                "phoneContact": [
                    {
                        "phoneType": "MOBILE_PHONE",
                        "countryCode": "+61",
                        "phoneNumber": f"04{random.randint(10000000, 99999999)}",
                        "contactPriority": "1"
                    }
                ],
                "emailContact": [
                    {
                        "emailAddress": f"customer{customer_id}@example.com"
                    }
                ],
                "alternativePartyIdReferences": [
                    {
                        "partyIdScheme": brand.upper(),
                        "partyIdRef": f"{brand.upper()}{customer_id}",
                        "partyIdStatus": "CUSTOMER"
                    }
                ],
                "extensionFields": [
                    {
                        "extensionFieldsType": "Customer",
                        "extensionFieldsCategory": "Audit",
                        "extensionField": [
                            {
                                "key": "createdDate",
                                "value": datetime.datetime.now().isoformat()
                            },
                            {
                                "key": "lastModifiedDate", 
                                "value": datetime.datetime.now().isoformat()
                            }
                        ]
                    }
                ]
            }
        }
    }
    
    # Add individual or organization specific data
    if customer_type.lower() == "individual":
        customer_data["attributes"]["partyDetails"]["individual"] = generate_sample_individual()
    else:
        customer_data["attributes"]["partyDetails"]["organisation"] = generate_sample_organization()
    
    return customer_data

# Initialize some sample data
def init_sample_data():
    """Initialize the database with sample customers"""
    brands = ["AAMI", "GIO", "APIA", "Bingle"]
    
    for _ in range(20):  # Create 20 sample customers
        brand = random.choice(brands)
        customer_type = random.choice(["individual", "organisation"])
        customer = generate_sample_customer(customer_type, brand)
        customers_db[customer["id"]] = customer
        
        # Add some vulnerabilities for random customers
        if random.choice([True, False, False]):  # 1/3 chance
            vulnerability_id = str(uuid.uuid4())
            vulnerabilities_db[customer["id"]] = {
                vulnerability_id: {
                    "id": vulnerability_id,
                    "type": "Vulnerability",
                    "attributes": {
                        "partyDetails": {
                            "partyIdRef": customer["id"],
                            "individual": {
                                "vulnerabilities": [
                                    {
                                        "vulnerabilityType": random.choice(["Family Violence", "Financial Hardship", "Mental Health", "Disability"]),
                                        "vulnerabilityStartDate": "2024-01-01 00:00:00.000",
                                        "vulnerabilityEndDate": "2025-12-31 23:59:59.999",
                                        "vulnerabilityNotes": [
                                            {
                                                "id": str(uuid.uuid4()),
                                                "type": "Vulnerability Note",
                                                "attributes": {
                                                    "note": "Customer requires additional support",
                                                    "extensionFields": []
                                                }
                                            }
                                        ]
                                    }
                                ]
                            },
                            "extensionFields": []
                        }
                    }
                }
            }

# OAuth Token Endpoint
@app.route('/oauth/token', methods=['POST'])
def get_token():
    """OAuth2 Client Credentials Grant"""
    
    # Handle both JSON and form data according to OAuth 2.0 spec
    if request.is_json:
        data = request.json
        client_id = data.get('client_id')
        client_secret = data.get('client_secret')
        grant_type = data.get('grant_type')
        scope = data.get('scope', '').split()
    else:
        # Form data (application/x-www-form-urlencoded)
        client_id = request.form.get('client_id')
        client_secret = request.form.get('client_secret') 
        grant_type = request.form.get('grant_type')
        scope = request.form.get('scope', '').split()
    
    logger.info(f"OAuth token request: client_id={client_id}, grant_type={grant_type}")
    
    # Validate grant type
    if grant_type != 'client_credentials':
        return create_error_response(400, "unsupported_grant_type", "Unsupported Grant Type", "Only client_credentials grant type is supported")
    
    # Validate client
    if client_id not in OAUTH_CLIENTS:
        return create_error_response(401, "invalid_client", "Invalid Client", "Client authentication failed")
    
    client = OAUTH_CLIENTS[client_id]
    if client['client_secret'] != client_secret:
        return create_error_response(401, "invalid_client", "Invalid Client", "Client authentication failed")
    
    # Filter requested scopes to only allowed scopes
    allowed_scopes = client['scopes']
    requested_scopes = scope if scope else allowed_scopes
    
    # Filter to only allowed scopes
    granted_scopes = [s for s in requested_scopes if s in allowed_scopes]
    if not granted_scopes and requested_scopes:
        granted_scopes = ['customer:read']  # Default scope
    elif not granted_scopes:
        granted_scopes = allowed_scopes  # Grant all allowed scopes
    
    # Generate JWT token
    now = datetime.datetime.now(datetime.timezone.utc)
    payload = {
        'iss': 'customer-api-test-server',  # issuer
        'sub': client_id,                   # subject (client)
        'iat': int(now.timestamp()),        # issued at
        'exp': int((now + datetime.timedelta(hours=1)).timestamp()),  # expires in 1 hour
        'scopes': granted_scopes,
        'client_id': client_id,
        'jti': str(uuid.uuid4())            # JWT ID
    }
    
    token = jwt.encode(payload, SECRET_KEY, algorithm=ALGORITHM)
    
    logger.info(f"Token granted: client_id={client_id}, scopes={granted_scopes}")
    
    return jsonify({
        'access_token': token,
        'token_type': 'Bearer',
        'expires_in': 3600,
        'scope': ' '.join(granted_scopes)
    }), 200

# Health Check Endpoint (no OAuth required)
@app.route('/api/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        "service": "customer-api-test-server",
        "status": "healthy",
        "timestamp": datetime.datetime.now().isoformat(),
        "version": "3.0.0",
        "endpoints": {
            "customers_search": "/v3/brands/{brand}/customers",
            "customers_create": "/v3/brands/{brand}/customers",
            "customers_get": "/v3/brands/{brand}/customers/{id}",
            "customers_update": "/v3/brands/{brand}/customers/{id}",
            "vulnerabilities": "/v3/brands/{brand}/customers/{id}/vulnerabilities",
            "lifecycle": "/v3/brands/{brand}/customers/lifecycle"
        }
    }), 200

# Customer Search Endpoint
@app.route('/v3/brands/<brand>/customers', methods=['GET'])
@require_auth(['customer:read'])
def search_customers(brand):
    """Search customers based on various criteria"""
    
    # Get search parameters
    first_name = request.args.get('firstName')
    last_name = request.args.get('lastName')
    registered_name = request.args.get('registeredName')
    email_address = request.args.get('emailAddress')
    phone_number = request.args.get('phoneNumber')
    party_id_scheme = request.args.get('partyIdScheme')
    party_id_ref = request.args.get('partyIdRef')
    policy_id = request.args.get('policyId')
    strict_match = request.args.get('strictMatch', 'false').lower() == 'true'
    limit = int(request.args.get('limit', 20))
    
    logger.info(f"Customer search: brand={brand}, firstName={first_name}, lastName={last_name}")
    
    # Filter customers based on search criteria
    results = []
    for customer_id, customer in customers_db.items():
        match = False
        
        # Check individual name matching
        if first_name or last_name:
            individual = customer.get("attributes", {}).get("partyDetails", {}).get("individual")
            if individual:
                if first_name and last_name:
                    if strict_match:
                        match = (individual.get("firstName", "").upper() == first_name.upper() and 
                                individual.get("lastName", "").upper() == last_name.upper())
                    else:
                        match = (first_name.upper() in individual.get("firstName", "").upper() and 
                                last_name.upper() in individual.get("lastName", "").upper())
                elif first_name:
                    if strict_match:
                        match = individual.get("firstName", "").upper() == first_name.upper()
                    else:
                        match = first_name.upper() in individual.get("firstName", "").upper()
                elif last_name:
                    if strict_match:
                        match = individual.get("lastName", "").upper() == last_name.upper()
                    else:
                        match = last_name.upper() in individual.get("lastName", "").upper()
        
        # Check organization name matching
        elif registered_name:
            organization = customer.get("attributes", {}).get("partyDetails", {}).get("organisation")
            if organization:
                if strict_match:
                    match = organization.get("registeredName", "").upper() == registered_name.upper()
                else:
                    match = registered_name.upper() in organization.get("registeredName", "").upper()
        
        # Check email matching
        elif email_address:
            email_contacts = customer.get("attributes", {}).get("partyDetails", {}).get("emailContact", [])
            for email_contact in email_contacts:
                if email_address.lower() == email_contact.get("emailAddress", "").lower():
                    match = True
                    break
        
        # Check phone matching
        elif phone_number:
            phone_contacts = customer.get("attributes", {}).get("partyDetails", {}).get("phoneContact", [])
            for phone_contact in phone_contacts:
                if phone_number == phone_contact.get("phoneNumber", ""):
                    match = True
                    break
        
        # Check party ID matching
        elif party_id_scheme and party_id_ref:
            alt_refs = customer.get("attributes", {}).get("partyDetails", {}).get("alternativePartyIdReferences", [])
            for alt_ref in alt_refs:
                if (alt_ref.get("partyIdScheme") == party_id_scheme and 
                    alt_ref.get("partyIdRef") == party_id_ref):
                    match = True
                    break
        
        # Default: return some customers for testing
        else:
            match = len(results) < limit
        
        if match:
            results.append(customer)
            if len(results) >= limit:
                break
    
    if not results:
        return create_error_response(404, "API-404", "No Matching Customers", "No customers found matching the search criteria")
    
    return jsonify(results), 200, {'Content-Type': 'application/vnd.api+json'}

# Create Customer Endpoint
@app.route('/v3/brands/<brand>/customers', methods=['POST'])
@require_auth(['customer:write'])
def create_customer(brand):
    """Create a new customer"""
    
    if not request.is_json:
        return create_error_response(400, "API-400", "Invalid Content Type", "Expected application/vnd.api+json")
    
    data = request.json
    
    # Validate required fields
    if not data or 'data' not in data:
        return create_error_response(400, "API-400", "Invalid Request", "Missing data object")
    
    customer_data = data['data']
    
    # Generate new customer ID
    customer_id = generate_customer_id()
    customer_data['id'] = customer_id
    
    # Add creation timestamp
    if 'attributes' not in customer_data:
        customer_data['attributes'] = {'partyDetails': {}}
    
    if 'extensionFields' not in customer_data['attributes']['partyDetails']:
        customer_data['attributes']['partyDetails']['extensionFields'] = []
    
    # Add creation audit fields
    creation_fields = {
        "extensionFieldsType": "Customer",
        "extensionFieldsCategory": "Audit",
        "extensionField": [
            {"key": "createdDate", "value": datetime.datetime.now().isoformat()},
            {"key": "createdBy", "value": request.token_info.get('client_id', 'system')},
            {"key": "brand", "value": brand}
        ]
    }
    customer_data['attributes']['partyDetails']['extensionFields'].append(creation_fields)
    
    # Store customer
    customers_db[customer_id] = customer_data
    
    logger.info(f"Customer created: id={customer_id}, brand={brand}")
    
    return jsonify({"data": customer_data}), 201, {'Content-Type': 'application/vnd.api+json'}

# Get Customer Endpoint
@app.route('/v3/brands/<brand>/customers/<customer_id>', methods=['GET'])
@require_auth(['customer:read'])
def get_customer(brand, customer_id):
    """Get a specific customer by ID"""
    
    if customer_id not in customers_db:
        return create_error_response(404, "API-404", "Customer Not Found", f"Customer with ID {customer_id} not found")
    
    customer = customers_db[customer_id]
    
    return jsonify({"data": customer}), 200, {'Content-Type': 'application/vnd.api+json'}

# Update Customer Endpoint
@app.route('/v3/brands/<brand>/customers/<customer_id>', methods=['PATCH'])
@require_auth(['customer:write'])
def update_customer(brand, customer_id):
    """Update an existing customer"""
    
    if customer_id not in customers_db:
        return create_error_response(404, "API-404", "Customer Not Found", f"Customer with ID {customer_id} not found")
    
    if not request.is_json:
        return create_error_response(400, "API-400", "Invalid Content Type", "Expected application/vnd.api+json")
    
    data = request.json
    if not data or 'data' not in data:
        return create_error_response(400, "API-400", "Invalid Request", "Missing data object")
    
    # Update customer (merge with existing data)
    existing_customer = customers_db[customer_id]
    update_data = data['data']
    
    # Merge attributes
    if 'attributes' in update_data:
        if 'attributes' not in existing_customer:
            existing_customer['attributes'] = {}
        existing_customer['attributes'].update(update_data['attributes'])
    
    # Add update audit fields
    if 'partyDetails' not in existing_customer['attributes']:
        existing_customer['attributes']['partyDetails'] = {}
    if 'extensionFields' not in existing_customer['attributes']['partyDetails']:
        existing_customer['attributes']['partyDetails']['extensionFields'] = []
    
    update_fields = {
        "extensionFieldsType": "Customer",
        "extensionFieldsCategory": "Audit",
        "extensionField": [
            {"key": "lastModifiedDate", "value": datetime.datetime.now().isoformat()},
            {"key": "lastModifiedBy", "value": request.token_info.get('client_id', 'system')}
        ]
    }
    existing_customer['attributes']['partyDetails']['extensionFields'].append(update_fields)
    
    customers_db[customer_id] = existing_customer
    
    logger.info(f"Customer updated: id={customer_id}, brand={brand}")
    
    return jsonify({"data": existing_customer}), 200, {'Content-Type': 'application/vnd.api+json'}

# Get Customer Vulnerabilities
@app.route('/v3/brands/<brand>/customers/<customer_id>/vulnerabilities', methods=['GET'])
@require_auth(['vulnerability:read'])
def get_customer_vulnerabilities(brand, customer_id):
    """Get vulnerabilities for a specific customer"""
    
    if customer_id not in customers_db:
        return create_error_response(404, "API-404", "Customer Not Found", f"Customer with ID {customer_id} not found")
    
    vulnerabilities = vulnerabilities_db.get(customer_id, {})
    vulnerability_list = list(vulnerabilities.values())
    
    return jsonify(vulnerability_list), 200, {'Content-Type': 'application/vnd.api+json'}

# Update Customer Vulnerability
@app.route('/v3/brands/<brand>/customers/<customer_id>/vulnerabilities/<vulnerability_id>', methods=['PATCH'])
@require_auth(['vulnerability:write'])
def update_customer_vulnerability(brand, customer_id, vulnerability_id):
    """Update a specific vulnerability for a customer"""
    
    if customer_id not in customers_db:
        return create_error_response(404, "API-404", "Customer Not Found", f"Customer with ID {customer_id} not found")
    
    if customer_id not in vulnerabilities_db or vulnerability_id not in vulnerabilities_db[customer_id]:
        return create_error_response(404, "API-404", "Vulnerability Not Found", f"Vulnerability with ID {vulnerability_id} not found")
    
    if not request.is_json:
        return create_error_response(400, "API-400", "Invalid Content Type", "Expected application/vnd.api+json")
    
    data = request.json
    if not data or 'data' not in data:
        return create_error_response(400, "API-400", "Invalid Request", "Missing data object")
    
    # Update vulnerability
    existing_vulnerability = vulnerabilities_db[customer_id][vulnerability_id]
    update_data = data['data']
    
    if 'attributes' in update_data:
        if 'attributes' not in existing_vulnerability:
            existing_vulnerability['attributes'] = {}
        existing_vulnerability['attributes'].update(update_data['attributes'])
    
    vulnerabilities_db[customer_id][vulnerability_id] = existing_vulnerability
    
    logger.info(f"Vulnerability updated: customer_id={customer_id}, vulnerability_id={vulnerability_id}")
    
    return jsonify({"data": existing_vulnerability}), 200, {'Content-Type': 'application/vnd.api+json'}

# Customer Lifecycle Operations
@app.route('/v3/brands/<brand>/customers/lifecycle', methods=['POST'])
@require_auth(['customer:write'])
def customer_lifecycle_operation(brand):
    """Perform lifecycle operations on customers"""
    
    if not request.is_json:
        return create_error_response(400, "API-400", "Invalid Content Type", "Expected application/vnd.api+json")
    
    data = request.json
    if not data or 'data' not in data:
        return create_error_response(400, "API-400", "Invalid Request", "Missing data object")
    
    lifecycle_data = data['data']
    
    # Create lifecycle event record
    lifecycle_event = {
        "id": str(uuid.uuid4()),
        "timestamp": datetime.datetime.now().isoformat(),
        "brand": brand,
        "operation": lifecycle_data.get('operation', 'unknown'),
        "client_id": request.token_info.get('client_id'),
        "data": lifecycle_data
    }
    
    lifecycle_events.append(lifecycle_event)
    
    logger.info(f"Lifecycle operation: brand={brand}, operation={lifecycle_event['operation']}")
    
    response_data = {
        "data": {
            "attributes": {
                "partyDetails": lifecycle_data.get('attributes', {}).get('partyDetails', {}),
            }
        }
    }
    
    return jsonify(response_data), 200, {'Content-Type': 'application/vnd.api+json'}

if __name__ == '__main__':
    init_sample_data()
    logger.info("Starting Customer API Test Server...")
    logger.info("Available endpoints:")
    logger.info("  Health Check: GET /api/health")
    logger.info("  OAuth Token: POST /oauth/token")
    logger.info("  Search Customers: GET /v3/brands/{brand}/customers")
    logger.info("  Create Customer: POST /v3/brands/{brand}/customers")
    logger.info("  Get Customer: GET /v3/brands/{brand}/customers/{id}")
    logger.info("  Update Customer: PATCH /v3/brands/{brand}/customers/{id}")
    logger.info("  Get Vulnerabilities: GET /v3/brands/{brand}/customers/{id}/vulnerabilities")
    logger.info("  Update Vulnerability: PATCH /v3/brands/{brand}/customers/{id}/vulnerabilities/{vulnerabilityId}")
    logger.info("  Lifecycle Operations: POST /v3/brands/{brand}/customers/lifecycle")
    logger.info("========================================")
    
    app.run(host='0.0.0.0', port=5050, debug=True)
