package com.guizmaii.scalajwt.zio

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.JWKSelector
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.proc.SecurityContext

/**
 * A JWKSource backed by a plain, synchronous supplier of the current JWKSet.
 *
 * The get() method is O(1) and never blocks - `currentJwks` is expected to be a lock-free read
 * (e.g. an AtomicReference#get, or a BackgroundCache#get). This allows us to implement the
 * synchronous JWKSource interface while keeping the cache updated from ZIO code elsewhere.
 */
final class AtomicJWKSource(currentJwks: () => JWKSet) extends JWKSource[SecurityContext] {
  override def get(selector: JWKSelector, context: SecurityContext): java.util.List[JWK] =
    selector.select(currentJwks())
}
