// Customer ratings/reviews of delivered order products. Mirrors inventory-service's Review domain
// (reviews live in inventory-service alongside Product - see ReviewController/ProductReviewController).

export interface Review {
  id: string
  productId: string
  productName?: string | null
  customerId: string
  // Only present on a product's public review list (see ProductReviewController.findByProduct) -
  // first name + last initial only, never the full name or any contact detail.
  customerDisplayName?: string | null
  orderId: string
  rating: number
  reviewText?: string | null
  createdAt: string
  updatedAt: string
}

export interface RatingSummary {
  productId: string
  averageRating: number
  totalReviews: number
  rating1Count: number
  rating2Count: number
  rating3Count: number
  rating4Count: number
  rating5Count: number
}

export interface CreateReviewRequest {
  orderId: string
  productId: string
  rating: number
  reviewText?: string
}

export interface UpdateReviewRequest {
  rating: number
  reviewText?: string
}
