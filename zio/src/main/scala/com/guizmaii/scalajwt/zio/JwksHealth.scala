package com.guizmaii.scalajwt.zio

import java.time.Instant

enum JwksHealth {
  case Healthy(lastRefresh: Instant, nextRefresh: Instant)
  case Degraded(lastRefresh: Instant, lastError: JwksFetchError)
}
