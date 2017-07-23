/*
 * Copyright (C) 2016 Apple Inc. All rights reserved.
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
 * THIS SOFTWARE IS PROVIDED BY APPLE INC. AND ITS CONTRIBUTORS ``AS IS''
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL APPLE INC. OR ITS CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

#include "config.h"
#include "NavigatorWebDriver.h"

#include "Frame.h"
#include "JSNavigator.h"
#include "Navigator.h"
#include "Page.h"
#include <JavaScriptCore/JSCInlines.h>

using namespace JSC;

namespace WebCore {

NavigatorWebDriver::NavigatorWebDriver(Frame* frame)
    : m_frame(frame)
{
}

NavigatorWebDriver::~NavigatorWebDriver()
{
}

const char* NavigatorWebDriver::supplementName()
{
    return "NavigatorWebDriver";
}

bool NavigatorWebDriver::isControlledByAutomation() const
{
    if (!m_frame || !m_frame->page())
        return false;

    return m_frame->page()->isControlledByAutomation();
}

NavigatorWebDriver* NavigatorWebDriver::from(Navigator* navigator)
{
    NavigatorWebDriver* supplement = static_cast<NavigatorWebDriver*>(Supplement<Navigator>::from(navigator, supplementName()));
    if (!supplement) {
        auto newSupplement = std::make_unique<NavigatorWebDriver>(navigator->frame());
        supplement = newSupplement.get();
        provideTo(navigator, supplementName(), WTFMove(newSupplement));
    }
    return supplement;
}

JSValue JSNavigator::webdriver(ExecState&) const
{
    bool isControlledByAutomation = NavigatorWebDriver::from(&wrapped())->isControlledByAutomation();
    return isControlledByAutomation ? jsBoolean(true) : jsUndefined();
}

} // namespace WebCore
