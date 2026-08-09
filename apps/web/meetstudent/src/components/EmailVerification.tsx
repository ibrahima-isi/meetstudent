import { useState, useEffect } from 'react';
import { Mail, CheckCircle, AlertCircle, RefreshCw } from 'lucide-react';

interface EmailVerificationProps {
  email: string;
  onVerificationSuccess: () => void;
}

export function EmailVerification({ email, onVerificationSuccess }: EmailVerificationProps) {
  const [code, setCode] = useState(['', '', '', '', '', '']);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [canResend, setCanResend] = useState(false);
  const [countdown, setCountdown] = useState(60);

  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    } else {
      setCanResend(true);
    }
  }, [countdown]);

  const handleCodeChange = (index: number, value: string) => {
    if (value.length > 1) return; // Only allow single digit
    
    const newCode = [...code];
    newCode[index] = value;
    setCode(newCode);
    setError('');

    // Auto-focus next input
    if (value && index < 5) {
      const nextInput = document.getElementById(`code-${index + 1}`);
      nextInput?.focus();
    }

    // Auto-submit when all fields are filled
    if (newCode.every(digit => digit !== '') && index === 5) {
      handleVerify(newCode.join(''));
    }
  };

  const handleKeyDown = (index: number, e: React.KeyboardEvent) => {
    if (e.key === 'Backspace' && !code[index] && index > 0) {
      const prevInput = document.getElementById(`code-${index - 1}`);
      prevInput?.focus();
    }
  };

  const handlePaste = (e: React.ClipboardEvent) => {
    e.preventDefault();
    const pastedData = e.clipboardData.getData('text').slice(0, 6);
    const newCode = pastedData.split('').concat(Array(6).fill('')).slice(0, 6);
    setCode(newCode);
    
    if (newCode.every(digit => digit !== '')) {
      handleVerify(newCode.join(''));
    }
  };

  const handleVerify = (verificationCode: string) => {
    setIsLoading(true);
    setError('');

    setTimeout(() => {
      const userData = localStorage.getItem(`user_${email}`);
      if (!userData) {
        setError('User not found. Please try registering again.');
        setIsLoading(false);
        return;
      }

      const user = JSON.parse(userData);
      
      if (verificationCode === user.verificationCode) {
        // Mark user as verified
        user.verified = true;
        localStorage.setItem(`user_${email}`, JSON.stringify(user));
        setIsLoading(false);
        
        // Show success message briefly before redirecting
        setTimeout(() => {
          onVerificationSuccess();
        }, 1500);
      } else {
        setError('Invalid verification code. Please try again.');
        setCode(['', '', '', '', '', '']);
        setIsLoading(false);
        document.getElementById('code-0')?.focus();
      }
    }, 1000);
  };

  const handleResend = () => {
    if (!canResend) return;
    
    setCanResend(false);
    setCountdown(60);
    setCode(['', '', '', '', '', '']);
    setError('');
    
    // In a real app, this would trigger a new verification email
    const userData = localStorage.getItem(`user_${email}`);
    if (userData) {
      const user = JSON.parse(userData);
      user.verificationCode = Math.floor(100000 + Math.random() * 900000).toString();
      localStorage.setItem(`user_${email}`, JSON.stringify(user));
    }
  };

  const verificationCode = (() => {
    const userData = localStorage.getItem(`user_${email}`);
    return userData ? JSON.parse(userData).verificationCode : '------';
  })();

  return (
    <div className="bg-white rounded-2xl shadow-xl p-8">
      <div className="text-center mb-8">
        <div className="inline-flex items-center justify-center w-16 h-16 bg-indigo-100 rounded-full mb-4">
          <Mail className="w-8 h-8 text-indigo-600" />
        </div>
        <h1 className="text-gray-900 mb-2">Verify Your Email</h1>
        <p className="text-gray-600">
          We've sent a verification code to
        </p>
        <p className="text-gray-900 mt-1">{email}</p>
      </div>

      {/* Demo info */}
      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 mb-6">
        <p className="text-blue-900">
          <span className="font-medium">Demo Mode:</span> Your verification code is{' '}
          <span className="font-mono bg-blue-100 px-2 py-1 rounded">{verificationCode}</span>
        </p>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-start gap-3 mb-6">
          <AlertCircle className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
          <p className="text-red-800">{error}</p>
        </div>
      )}

      {isLoading && code.every(d => d !== '') && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-4 flex items-start gap-3 mb-6">
          <CheckCircle className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
          <p className="text-green-800">Email verified successfully! Redirecting...</p>
        </div>
      )}

      <div className="mb-8">
        <label className="block text-gray-700 mb-4 text-center">
          Enter Verification Code
        </label>
        <div className="flex justify-center gap-3">
          {code.map((digit, index) => (
            <input
              key={index}
              id={`code-${index}`}
              type="text"
              inputMode="numeric"
              maxLength={1}
              value={digit}
              onChange={(e) => handleCodeChange(index, e.target.value)}
              onKeyDown={(e) => handleKeyDown(index, e)}
              onPaste={index === 0 ? handlePaste : undefined}
              className="w-12 h-14 text-center border-2 border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent disabled:opacity-50"
              disabled={isLoading}
            />
          ))}
        </div>
      </div>

      <button
        onClick={handleResend}
        disabled={!canResend}
        className="w-full flex items-center justify-center gap-2 text-indigo-600 hover:text-indigo-700 disabled:text-gray-400 disabled:cursor-not-allowed mb-6"
      >
        <RefreshCw className="w-4 h-4" />
        {canResend ? 'Resend Code' : `Resend in ${countdown}s`}
      </button>

      <div className="text-center">
        <p className="text-gray-600">
          Wrong email?{' '}
          <button
            onClick={onVerificationSuccess}
            className="text-indigo-600 hover:text-indigo-700"
          >
            Go back
          </button>
        </p>
      </div>
    </div>
  );
}
