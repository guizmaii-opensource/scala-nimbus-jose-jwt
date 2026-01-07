package com.guizmaii.scalajwt.zio

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.JWKSelector
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.proc.SecurityContext

import java.util.concurrent.atomic.AtomicReference

/**
 * A JWKSource that reads from an AtomicReference[JWKSet].
 *
 * The get() method is O(1) and never blocks - it's just a volatile read.
 * This allows us to implement the synchronous JWKSource interface while
 * keeping the cache updated from ZIO code via the AtomicReference.
 */
final class AtomicJWKSource(ref: AtomicReference[JWKSet]) extends JWKSource[SecurityContext] {
  override def get(selector: JWKSelector, context: SecurityContext): java.util.List[JWK] =
    selector.select(ref.get())
}
