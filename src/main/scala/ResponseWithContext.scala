/** Container holding
 *   - http response (e.g. tapir high-level case class),
 *   - logging context of the response (e.g correlation id extracted from http headers)
 *
 * Captured logging context allows to match server-side and client-side log (by correlation id).
 */
case class ResponseWithContext[A](ctx: LoggingContext, response: A)
