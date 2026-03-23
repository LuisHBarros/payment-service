com.payment.paymentservice
├── user
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   ├── dto
│   └── exception
│
├── wallet
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   ├── dto
│   └── exception
│
├── transfer
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   ├── dto
│   └── exception
│
├── common
│   ├── exception
│   ├── handler
│   └── util
│
└── config



POST   /api/v1/users
GET    /api/v1/users
GET    /api/v1/users/{id}
PUT    /api/v1/users/{id}
PATCH  /api/v1/users/{id}
DELETE /api/v1/users/{id}

GET    /api/v1/users/{id}/wallet
POST   /api/v1/users/{id}/wallet/deposit
POST   /api/v1/users/{id}/wallet/withdraw
PATCH  /api/v1/users/{id}/wallet

POST   /api/v1/transfers
GET    /api/v1/transfers
GET    /api/v1/transfers/{id}

GET    /api/v1/users/{id}/transfers