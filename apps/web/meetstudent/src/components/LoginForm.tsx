import { useState } from 'react';
import { Mail, Lock, LogIn, AlertCircle, CheckCircle } from 'lucide-react';

interface LoginFormProps {
  onSwitchToRegister: () => void;
  onLoginSuccess: () => void;
}

export function LoginForm({ onSwitchToRegister, onLoginSuccess }: LoginFormProps) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setIsLoading(true);

    // Simulate API call
    setTimeout(() => {
      // Mock validation
      const storedUser = localStorage.getItem(`user_${email}`);
      
      if (!storedUser) {
        setError('No account found with this email. Please register first.');
        setIsLoading(false);
        return;
      }

      const userData = JSON.parse(storedUser);
      
      if (!userData.verified) {
        setError('Please verify your email before logging in.');
        setIsLoading(false);
        return;
      }

      if (userData.password !== password) {
        setError('Incorrect password. Please try again.');
        setIsLoading(false);
        return;
      }

      setSuccess('Login successful! Welcome back.');
      setIsLoading(false);
      
      // Redirect to home after a short delay
      setTimeout(() => {
        onLoginSuccess();
      }, 1000);
    }, 1000);
  };

  const handleOAuthLogin = (provider: 'google' | 'microsoft') => {
    setError('');
    setSuccess('');
    setIsLoading(true);

    // Simulate OAuth flow
    setTimeout(() => {
      setSuccess(`Successfully logged in with ${provider === 'google' ? 'Google' : 'Microsoft'}!`);
      setIsLoading(false);
      
      // Redirect to home after a short delay
      setTimeout(() => {
        onLoginSuccess();
      }, 1000);
    }, 1500);
  };

  return (
    <div className="bg-white rounded-2xl shadow-xl p-8">
      <div className="text-center mb-8">
        <div className="inline-flex items-center justify-center w-16 h-16 bg-indigo-100 rounded-full mb-4">
          <LogIn className="w-8 h-8 text-indigo-600" />
        </div>
        <h1 className="text-gray-900 mb-2">Welcome Back</h1>
        <p className="text-gray-600">Sign in to your account</p>
      </div>

      {/* OAuth Buttons */}
      <div className="space-y-3 mb-6">
        <button
          type="button"
          onClick={() => handleOAuthLogin('google')}
          disabled={isLoading}
          className="w-full flex items-center justify-center gap-3 px-4 py-3 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <svg className="w-5 h-5" viewBox="0 0 24 24">
            <path
              fill="#4285F4"
              d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
            />
            <path
              fill="#34A853"
              d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
            />
            <path
              fill="#FBBC05"
              d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
            />
            <path
              fill="#EA4335"
              d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
            />
          </svg>
          <span>Continue with Google</span>
        </button>

        <button
          type="button"
          onClick={() => handleOAuthLogin('microsoft')}
          disabled={isLoading}
          className="w-full flex items-center justify-center gap-3 px-4 py-3 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <svg className="w-5 h-5" viewBox="0 0 23 23">
            <path fill="#f3f3f3" d="M0 0h23v23H0z" />
            <path fill="#f35325" d="M1 1h10v10H1z" />
            <path fill="#81bc06" d="M12 1h10v10H12z" />
            <path fill="#05a6f0" d="M1 12h10v10H1z" />
            <path fill="#ffba08" d="M12 12h10v10H12z" />
          </svg>
          <span>Continue with Microsoft</span>
        </button>
      </div>

      {/* Divider */}
      <div className="relative mb-6">
        <div className="absolute inset-0 flex items-center">
          <div className="w-full border-t border-gray-300"></div>
        </div>
        <div className="relative flex justify-center">
          <span className="px-4 bg-white text-gray-500">Or continue with email</span>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="space-y-5">
        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-start gap-3">
            <AlertCircle className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
            <p className="text-red-800">{error}</p>
          </div>
        )}

        {success && (
          <div className="bg-green-50 border border-green-200 rounded-lg p-4 flex items-start gap-3">
            <CheckCircle className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
            <p className="text-green-800">{success}</p>
          </div>
        )}

        <div>
          <label htmlFor="email" className="block text-gray-700 mb-2">
            Email Address
          </label>
          <div className="relative">
            <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
              placeholder="you@example.com"
              required
            />
          </div>
        </div>

        <div>
          <label htmlFor="password" className="block text-gray-700 mb-2">
            Password
          </label>
          <div className="relative">
            <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
              placeholder="••••••••"
              required
            />
          </div>
        </div>

        <div className="flex items-center justify-between">
          <label className="flex items-center">
            <input
              type="checkbox"
              className="w-4 h-4 text-indigo-600 border-gray-300 rounded focus:ring-indigo-500"
            />
            <span className="ml-2 text-gray-700">Remember me</span>
          </label>
          <button type="button" className="text-indigo-600 hover:text-indigo-700">
            Forgot password?
          </button>
        </div>

        <button
          type="submit"
          disabled={isLoading}
          className="w-full flex items-center justify-center bg-indigo-600 text-white py-3 rounded-lg hover:bg-indigo-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isLoading ? 'Signing in...' : 'Sign In'}
        </button>
      </form>

      <div className="mt-6 text-center">
        <p className="text-gray-600">
          Don't have an account?{' '}
          <button
            onClick={onSwitchToRegister}
            className="text-indigo-600 hover:text-indigo-700"
          >
            Sign up
          </button>
        </p>
      </div>
    </div>
  );
}
