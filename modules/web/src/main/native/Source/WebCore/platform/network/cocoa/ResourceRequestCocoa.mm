/*
 * Copyright (C) 2014-2017 Apple, Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY APPLE INC. ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL APPLE INC. OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#import "config.h"
#import "ResourceRequest.h"

#if PLATFORM(COCOA)

#import "FileSystem.h"
#import "FormDataStreamMac.h"
#import "HTTPHeaderNames.h"
#import "ResourceRequestCFNet.h"
#import "RuntimeApplicationChecks.h"
#import "WebCoreSystemInterface.h"

#import <Foundation/Foundation.h>
#import <wtf/text/CString.h>

@interface NSURLRequest (WebNSURLRequestDetails)
- (NSArray *)contentDispositionEncodingFallbackArray;
+ (void)setDefaultTimeoutInterval:(NSTimeInterval)seconds;
- (CFURLRequestRef)_CFURLRequest;
- (id)_initWithCFURLRequest:(CFURLRequestRef)request;
@end

@interface NSMutableURLRequest (WebMutableNSURLRequestDetails)
- (void)setContentDispositionEncodingFallbackArray:(NSArray *)theEncodingFallbackArray;
@end

namespace WebCore {

NSURLRequest *ResourceRequest::nsURLRequest(HTTPBodyUpdatePolicy bodyPolicy) const
{
    updatePlatformRequest(bodyPolicy);
#if USE(CFURLCONNECTION)
    if (!m_nsRequest)
        const_cast<ResourceRequest*>(this)->updateNSURLRequest();
#endif
    return [[m_nsRequest.get() retain] autorelease];
}

#if USE(CFURLCONNECTION)

void ResourceRequest::clearOrUpdateNSURLRequest()
{
    // There is client code that extends NSURLRequest and expects to get back, in the delegate
    // callbacks, an object of the same type that they passed into WebKit. To keep then running, we
    // create an object of the same type and return that. See <rdar://9843582>.
    // Also, developers really really want an NSMutableURLRequest so try to create an
    // NSMutableURLRequest instead of NSURLRequest.
    static Class nsURLRequestClass = [NSURLRequest class];
    static Class nsMutableURLRequestClass = [NSMutableURLRequest class];
    Class requestClass = [m_nsRequest.get() class];
    
    if (!m_cfRequest)
        return;
    
    if (requestClass && requestClass != nsURLRequestClass && requestClass != nsMutableURLRequestClass)
        m_nsRequest = adoptNS([[requestClass alloc] _initWithCFURLRequest:m_cfRequest.get()]);
    else
        m_nsRequest = nullptr;
}

#else

CFURLRequestRef ResourceRequest::cfURLRequest(HTTPBodyUpdatePolicy bodyPolicy) const
{
    return [nsURLRequest(bodyPolicy) _CFURLRequest];
}

static inline ResourceRequestCachePolicy fromPlatformRequestCachePolicy(NSURLRequestCachePolicy policy)
{
    switch (policy) {
    case NSURLRequestUseProtocolCachePolicy:
        return UseProtocolCachePolicy;
    case NSURLRequestReturnCacheDataElseLoad:
        return ReturnCacheDataElseLoad;
    case NSURLRequestReturnCacheDataDontLoad:
        return ReturnCacheDataDontLoad;
    default:
        return ReloadIgnoringCacheData;
    }
}

static inline NSURLRequestCachePolicy toPlatformRequestCachePolicy(ResourceRequestCachePolicy policy)
{
    switch (policy) {
    case UseProtocolCachePolicy:
        return NSURLRequestUseProtocolCachePolicy;
    case ReturnCacheDataElseLoad:
        return NSURLRequestReturnCacheDataElseLoad;
    case ReturnCacheDataDontLoad:
        return NSURLRequestReturnCacheDataDontLoad;
    default:
        return NSURLRequestReloadIgnoringLocalCacheData;
    }
}

void ResourceRequest::doUpdateResourceRequest()
{
    m_url = [m_nsRequest.get() URL];

    if (!m_cachePolicy)
        m_cachePolicy = fromPlatformRequestCachePolicy([m_nsRequest.get() cachePolicy]);
    m_timeoutInterval = [m_nsRequest.get() timeoutInterval];
    m_firstPartyForCookies = [m_nsRequest.get() mainDocumentURL];

    if (NSString* method = [m_nsRequest.get() HTTPMethod])
        m_httpMethod = method;
    m_allowCookies = [m_nsRequest.get() HTTPShouldHandleCookies];

    if (resourcePrioritiesEnabled())
        m_priority = toResourceLoadPriority(m_nsRequest ? CFURLRequestGetRequestPriority([m_nsRequest _CFURLRequest]) : 0);

    m_httpHeaderFields.clear();
    [[m_nsRequest allHTTPHeaderFields] enumerateKeysAndObjectsUsingBlock: ^(NSString *name, NSString *value, BOOL *) {
        m_httpHeaderFields.set(name, value);
    }];

    m_responseContentDispositionEncodingFallbackArray.clear();
    NSArray *encodingFallbacks = [m_nsRequest.get() contentDispositionEncodingFallbackArray];
    m_responseContentDispositionEncodingFallbackArray.reserveCapacity([encodingFallbacks count]);
    for (NSNumber *encodingFallback in [m_nsRequest contentDispositionEncodingFallbackArray]) {
        CFStringEncoding encoding = CFStringConvertNSStringEncodingToEncoding([encodingFallback unsignedLongValue]);
        if (encoding != kCFStringEncodingInvalidId)
            m_responseContentDispositionEncodingFallbackArray.uncheckedAppend(CFStringConvertEncodingToIANACharSetName(encoding));
    }

    if (m_nsRequest) {
        NSString* cachePartition = [NSURLProtocol propertyForKey:(NSString *)wkCachePartitionKey() inRequest:m_nsRequest.get()];
        if (cachePartition)
            m_cachePartition = cachePartition;
    }
}

void ResourceRequest::doUpdateResourceHTTPBody()
{
    if (NSData* bodyData = [m_nsRequest.get() HTTPBody])
        m_httpBody = FormData::create([bodyData bytes], [bodyData length]);
    else if (NSInputStream* bodyStream = [m_nsRequest.get() HTTPBodyStream]) {
        FormData* formData = httpBodyFromStream(bodyStream);
        // There is no FormData object if a client provided a custom data stream.
        // We shouldn't be looking at http body after client callbacks.
        ASSERT(formData);
        if (formData)
            m_httpBody = formData;
    }
}

void ResourceRequest::doUpdatePlatformRequest()
{
    if (isNull()) {
        m_nsRequest = nil;
        return;
    }

    NSMutableURLRequest *nsRequest = [m_nsRequest.get() mutableCopy];

    if (nsRequest)
        [nsRequest setURL:url()];
    else
        nsRequest = [[NSMutableURLRequest alloc] initWithURL:url()];

    if (ResourceRequest::httpPipeliningEnabled())
        CFURLRequestSetShouldPipelineHTTP([nsRequest _CFURLRequest], true, true);

    if (ResourceRequest::resourcePrioritiesEnabled())
        CFURLRequestSetRequestPriority([nsRequest _CFURLRequest], toPlatformRequestPriority(priority()));

    [nsRequest setCachePolicy:toPlatformRequestCachePolicy(cachePolicy())];
    _CFURLRequestSetProtocolProperty([nsRequest _CFURLRequest], kCFURLRequestAllowAllPOSTCaching, kCFBooleanTrue);

    double timeoutInterval = ResourceRequestBase::timeoutInterval();
    if (timeoutInterval)
        [nsRequest setTimeoutInterval:timeoutInterval];
    // Otherwise, respect NSURLRequest default timeout.

    [nsRequest setMainDocumentURL:firstPartyForCookies()];
    if (!httpMethod().isEmpty())
        [nsRequest setHTTPMethod:httpMethod()];
    [nsRequest setHTTPShouldHandleCookies:allowCookies()];

    // Cannot just use setAllHTTPHeaderFields here, because it does not remove headers.
    for (NSString *oldHeaderName in [nsRequest allHTTPHeaderFields])
        [nsRequest setValue:nil forHTTPHeaderField:oldHeaderName];
    for (const auto& header : httpHeaderFields())
        [nsRequest setValue:header.value forHTTPHeaderField:header.key];

    NSMutableArray *encodingFallbacks = [NSMutableArray array];
    for (const auto& encodingName : m_responseContentDispositionEncodingFallbackArray) {
        CFStringEncoding nsEncoding = CFStringConvertEncodingToNSStringEncoding(CFStringConvertIANACharSetNameToEncoding(encodingName.createCFString().get()));
        if (nsEncoding != kCFStringEncodingInvalidId)
            [encodingFallbacks addObject:[NSNumber numberWithUnsignedLong:nsEncoding]];
    }
    [nsRequest setContentDispositionEncodingFallbackArray:encodingFallbacks];

    String partition = cachePartition();
    if (!partition.isNull() && !partition.isEmpty()) {
        NSString *partitionValue = [NSString stringWithUTF8String:partition.utf8().data()];
        [NSURLProtocol setProperty:partitionValue forKey:(NSString *)wkCachePartitionKey() inRequest:nsRequest];
    }

#if (PLATFORM(MAC) && __MAC_OS_X_VERSION_MIN_REQUIRED >= 101200)
    if (m_url.isLocalFile()) {
        auto fsRepFile = fileSystemRepresentation(m_url.fileSystemPath());
        if (!fsRepFile.isNull()) {
            auto fileDevice = getFileDeviceId(fsRepFile);
            if (fileDevice && fileDevice.value())
                [nsRequest _setProperty:[NSNumber numberWithInteger:fileDevice.value()] forKey:@"NSURLRequestFileProtocolExpectedDevice"];
        }
    }
#endif

    m_nsRequest = adoptNS(nsRequest);
}

void ResourceRequest::doUpdatePlatformHTTPBody()
{
    if (isNull()) {
        ASSERT(!m_nsRequest);
        return;
    }

    NSMutableURLRequest *nsRequest = [m_nsRequest.get() mutableCopy];

    if (nsRequest)
        [nsRequest setURL:url()];
    else
        nsRequest = [[NSMutableURLRequest alloc] initWithURL:url()];

    FormData* formData = httpBody();
    if (formData && !formData->isEmpty())
        WebCore::setHTTPBody(nsRequest, formData);

    if (NSInputStream *bodyStream = [nsRequest HTTPBodyStream]) {
        // For streams, provide a Content-Length to avoid using chunked encoding, and to get accurate total length in callbacks.
        NSString *lengthString = [bodyStream propertyForKey:(NSString *)formDataStreamLengthPropertyName()];
        if (lengthString) {
            [nsRequest setValue:lengthString forHTTPHeaderField:@"Content-Length"];
            // Since resource request is already marked updated, we need to keep it up to date too.
            ASSERT(m_resourceRequestUpdated);
            m_httpHeaderFields.set(HTTPHeaderName::ContentLength, lengthString);
        }
    }

    m_nsRequest = adoptNS(nsRequest);
}

void ResourceRequest::setStorageSession(CFURLStorageSessionRef storageSession)
{
    updatePlatformRequest();
    m_nsRequest = adoptNS(wkCopyRequestWithStorageSession(storageSession, m_nsRequest.get()));
}

#endif // USE(CFURLCONNECTION)

} // namespace WebCore

#endif // PLATFORM(COCOA)

